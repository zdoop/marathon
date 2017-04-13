package mesosphere.marathon
package api.v2

import javax.inject.Inject
import javax.servlet.http.HttpServletRequest
import javax.ws.rs._
import javax.ws.rs.core.{ Context, Response }

import com.wix.accord.Validator
import mesosphere.marathon.api._
import mesosphere.marathon.api.v2.AppsResource.NormalizationConfig
import mesosphere.marathon.api.v2.Validation.validateOrThrow
import mesosphere.marathon.api.v2.validation.PodsValidation
import mesosphere.marathon.core.base.Clock
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.core.pod.PodDefinition
import mesosphere.marathon.plugin.auth._
import mesosphere.marathon.raml.{ Pod, Raml }
import mesosphere.marathon.state.{ AppDefinition, VersionInfo }
import mesosphere.marathon.util.SemanticVersion
import play.api.libs.json.{ JsError, JsResultException, JsSuccess, Json }

@Path("v2/validations")
class ValidationsResource @Inject() (
    clock: Clock,
    val config: MarathonConf,
    val authenticator: Authenticator,
    val authorizer: Authorizer,
    pluginManager: PluginManager) extends AuthResource {

  private implicit lazy val appDefinitionValidator = AppDefinition.validAppDefinition(config.availableFeatures)(pluginManager)
  private val normalizationConfig = AppNormalization.Configure(config.defaultNetworkName.get)

  private implicit val validateAndNormalizeApp: Normalization[raml.App] =
    AppsResource.appNormalization(NormalizationConfig(config.availableFeatures, normalizationConfig))(AppNormalization.withCanonizedIds())

  implicit def podDefValidator: Validator[Pod] =
    PodsValidation.podDefValidator(
      config.availableFeatures,
      SemanticVersion(0, 0, 0))

  private def normalizeApp(app: raml.App): raml.App = validateAndNormalizeApp.normalized(app)

  private def normalizePod(pod: raml.Pod): raml.Pod = PodsResource.normalize(pod, config)

  private def normalizePod(pod: PodDefinition): PodDefinition = pod.copy(version = clock.now())

  @POST
  def validateApp(
    body: Array[Byte],
    @Context req: HttpServletRequest): Response = authenticated(req) { implicit identity =>

    assumeValid {
      val deserializedApp = raml.App.playJsonFormat.reads(Json.parse(body), strict = true)
      val rawApp: AppDefinition = deserializedApp match {
        case success: JsSuccess[raml.App] => Raml.fromRaml(normalizeApp(success.get))
        case error: JsError => throw JsResultException(error.errors)
      }
      val now = clock.now()
      val app = validateOrThrow(rawApp).copy(versionInfo = VersionInfo.OnlyVersion(now))
      checkAuthorization(CreateRunSpec, app)

      Response
        .ok()
        .build()
    }
  }

  @POST
  def validatePod(
    body: Array[Byte],
    @Context req: HttpServletRequest): Response = {
    authenticated(req) { implicit identity =>
      val deserializedPod = raml.Pod.playJsonFormat.reads(Json.parse(body), strict = true)
      val rawPod: Pod = deserializedPod match {
        case success: JsSuccess[raml.Pod] => normalizePod(success.value)
        case error: JsError => throw JsResultException(error.errors)
      }
      withValid(rawPod) { podDef =>
        val pod = normalizePod(Raml.fromRaml(normalizePod(podDef)))
        withAuthorization(CreateRunSpec, pod) {

          Response
            .ok()
            .build()
        }
      }
    }
  }
}
