package mesosphere.marathon
package core.scheduling.behavior
package impl
import akka.Done
import mesosphere.marathon.core.instance.update.InstanceChange

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Responsible for handling instance changes by delegating the decisions to
  * be made to a specific behavior based on the scheduling of the associated
  * run spec.
  */
final class InstanceChangeBehaviorImpl(continuousBehavior: ContinuousBehavior) extends InstanceChangeBehavior {

  override def handle(change: InstanceChange)(implicit ec: ExecutionContext): Future[Done] = {
    // this will forward the decision to a specific behavioral definition in the future
    // since the continuous behavior is currently the only available one, this is hard coded
    continuousBehavior.handle(change)
  }

}
