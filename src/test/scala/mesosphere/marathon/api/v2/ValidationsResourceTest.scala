package mesosphere.marathon
package api.v2

import java.util.Collections

import mesosphere.AkkaUnitTest
import mesosphere.marathon.api.TestAuthFixture
import mesosphere.marathon.core.appinfo._
import mesosphere.marathon.core.base.Clock
import mesosphere.marathon.core.group.GroupManager
import mesosphere.marathon.core.plugin.PluginManager
import mesosphere.marathon.plugin.auth.{ Authenticator, Authorizer }
import mesosphere.marathon.raml.{ App, MesosExec, Pod, PodContainer, Resources, ShellCommand }
import mesosphere.marathon.storage.repository.GroupRepository
import mesosphere.marathon.test.GroupCreation
import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.concurrent.duration._

class ValidationsResourceTest extends AkkaUnitTest with GroupCreation {

  case class Fixture(
      config: MarathonConf = mock[MarathonConf],
      groupManager: GroupManager = mock[GroupManager],
      groupRepository: GroupRepository = mock[GroupRepository],
      auth: TestAuthFixture = new TestAuthFixture,
      groupInfo: GroupInfoService = mock[GroupInfoService],
      embed: java.util.Set[String] = Collections.emptySet[String]) {
    config.zkTimeoutDuration returns (patienceConfig.timeout.toMillis * 2).millis
    config.availableFeatures returns Set.empty
    config.defaultNetworkName returns new org.rogach.scallop.ScallopOption[String]("default_network_name") {}

    val clock = Clock()
    implicit val authz: Authorizer = auth.auth
    implicit val authn: Authenticator = auth.auth
    val validationsResource: ValidationsResource = new ValidationsResource(clock, config, authn, authz, PluginManager.None)
  }

  "ValidationsResource" should {
    "validate a valid app" in new Fixture {
      Given("app definition")

      val app = App(id = "/test/app", cmd = Some("test cmd"))

      val body = Json.stringify(Json.toJson(app)).getBytes
      val result = validationsResource.validateApp(body, auth.request)

      Then("http result is 200")
      result.getStatus shouldEqual 200

    }
    "validate a invalid app" in new Fixture {
      Given("app definition")

      val app = App(id = "/test/app")

      val body = Json.stringify(Json.toJson(app)).getBytes
      val result = validationsResource.validateApp(body, auth.request)

      Then("http result is 422 and correct error")
      result.getStatus shouldEqual 422

      val json = Json.parse(result.getEntity.toString)
      (json \ "message") should be(JsDefined(JsString("Object is not valid")))
      (json \ "details" \ 0 \ "path") should be(JsDefined(JsString("/")))
    }

    "validate a valid pod" in new Fixture {
      Given("pod definition")

      val container = PodContainer(name = "first", exec = Some(MesosExec(command = ShellCommand("sleep 100"))), resources = Resources())
      val containers: Seq[PodContainer] = Seq(container)
      val pod = Pod(id = "/test/pod", containers = containers)

      val body = Json.stringify(Json.toJson(pod)).getBytes
      val result = validationsResource.validatePod(body, auth.request)

      Then("http result is 200")
      result.getStatus shouldEqual 200

    }
    "validate a invalid pod" in new Fixture {
      Given("pod definition")

      val pod = Pod(id = "/test/pod", containers = Seq.empty[PodContainer])

      val body = Json.stringify(Json.toJson(pod)).getBytes
      val ex = intercept[JsResultException](validationsResource.validatePod(body, auth.request))

      Then("correct error")
      val error = ex.errors.head
      error._1 should be(JsPath(List(KeyPathNode("containers"))))
      error._2.head should be(ValidationError("error.minLength", 1))
    }

    "validate a app with unknown properties" in new Fixture {
      Given("app definition")

      val body =
        """{
          |  "id": "/test",
          |  "cmd": "sleep 10",
          |  "foo" : "bar"
          |}""".stripMargin.getBytes("UTF-8")

      val result = validationsResource.validateApp(body, auth.request)

      Then("http result is 422 and correct error")
      result.getStatus shouldEqual 422

      val json = Json.parse(result.getEntity.toString)
      (json \ "message") should be(JsDefined(JsString("Invalid JSON")))
      (json \ "details" \ 0 \ "path") should be(JsDefined(JsString("/foo")))
    }
  }
}
