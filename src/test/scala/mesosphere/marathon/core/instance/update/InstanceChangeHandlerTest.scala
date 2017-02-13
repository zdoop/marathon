package mesosphere.marathon
package core.instance.update

import akka.Done
import mesosphere.UnitTest
import mesosphere.marathon.core.instance.TestInstanceBuilder
import mesosphere.marathon.core.task.bus.TaskStatusUpdateTestHelper
import mesosphere.marathon.state.PathId
import mesosphere.marathon.test.CaptureLogEvents

import scala.concurrent.Future

class InstanceChangeHandlerTest extends UnitTest {
  "ContinueOnError" should {
    "A successful step should not produce logging output" in {
      val f = new Fixture
      Given("a nested step that is always successful")
      val handler = f.successfulHandler

      When("executing the step")
      val logEvents = CaptureLogEvents.forBlock {
        val resultFuture = handler.process(f.runningUpdate())
        resultFuture.futureValue
      }

      And("not produce any logging output")
      logEvents.filter(_.getMessage.contains(s"[${f.dummyInstance.instanceId.idString}]")) should be(empty)
    }

    "A failing step should log the error but proceed" in {
      val f = new Fixture
      Given("a nested step that always fails")
      val handler = f.failingHandler

      When("executing the step")
      val logEvents = CaptureLogEvents.forBlock {
        val resultFuture = handler.process(f.runningUpdate())
        resultFuture.futureValue
      }

      And("produce an error message in the log")
      logEvents.map(_.toString) should contain(
        s"[ERROR] while executing step nested for [${f.dummyInstance.instanceId.idString}], continue with other steps"
      )
    }
  }
  class Fixture {
    private[this] val appId: PathId = PathId("/test")
    val dummyInstanceBuilder = TestInstanceBuilder.newBuilderWithLaunchedTask(appId)
    val dummyInstance = dummyInstanceBuilder.getInstance()
    val successfulHandler = new InstanceChangeHandler {
      override def name = "nested"
      override def process(update: InstanceChange): Future[Done] = continueOnError(name, update) { update =>
        Future.successful(Done)
      }
    }
    val failingHandler = new InstanceChangeHandler {
      override def name = "nested"
      override def process(update: InstanceChange): Future[Done] = continueOnError(name, update) { update =>
        Future.failed(new RuntimeException("failed"))
      }
    }

    def runningUpdate(): InstanceChange = TaskStatusUpdateTestHelper.running(dummyInstanceBuilder.getInstance()).wrapped
  }
}
