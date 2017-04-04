package mesosphere.marathon
package api.v2

import java.net.URI
import javax.inject.Inject
import javax.servlet.http.HttpServletRequest
import javax.ws.rs._
import javax.ws.rs.core.{ Context, Response }

import mesosphere.marathon.api._
import mesosphere.marathon.api.v2.AppsResource.NormalizationConfig
import mesosphere.marathon.api.v2.Validation.validateOrThrow
import mesosphere.marathon.core.async.ExecutionContexts
import mesosphere.marathon.core.base.Clock
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.plugin.auth._
import mesosphere.marathon.raml.Raml
import mesosphere.marathon.state.{ AppDefinition, VersionInfo }
import org.slf4j.LoggerFactory
import play.api.libs.json.Json

@Path("v2/validations")
class ValidationsResource @Inject() (
    clock: Clock,
    val config: MarathonConf,
    val authenticator: Authenticator,
    val authorizer: Authorizer,
    pluginManager: PluginManager) extends AuthResource {

  val log = LoggerFactory.getLogger(getClass.getName)
  implicit val ec = ExecutionContexts.global

  private implicit lazy val appDefinitionValidator = AppDefinition.validAppDefinition(config.availableFeatures)(pluginManager)
  private val normalizationConfig = AppNormalization.Configure(config.defaultNetworkName.get)

  private implicit val validateAndNormalizeApp: Normalization[raml.App] =
    AppsResource.appNormalization(NormalizationConfig(config.availableFeatures, normalizationConfig))(AppNormalization.withCanonizedIds())

  @POST
  def create(
    body: Array[Byte],
    @Context req: HttpServletRequest): Response = authenticated(req) { implicit identity =>

    assumeValid {
      val rawApp = Raml.fromRaml(normalize(Json.parse(body).as[raml.App]))
      val now = clock.now()
      val app = validateOrThrow(rawApp).copy(versionInfo = VersionInfo.OnlyVersion(now))

      checkAuthorization(CreateRunSpec, app)

      Response
        .ok(new URI(app.id.toString))
        .build()
    }
  }

  def normalize(app: raml.App): raml.App = validateAndNormalizeApp.normalized(app)
}
