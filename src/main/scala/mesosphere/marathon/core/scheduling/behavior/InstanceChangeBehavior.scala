package mesosphere.marathon
package core.scheduling.behavior

import akka.Done
import mesosphere.marathon.core.instance.update.InstanceChange

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Defines the behavior that shall be applied when an instance changes its
  * state. In particular, this defines which components must be notified, and
  * which steps must be processed so that the system can react in a meaningful
  * way.
  *
  * The behavior will be called after an instance state change has been
  * persisted in the repository
  */
trait InstanceChangeBehavior {

  def handle(change: InstanceChange)(implicit ec: ExecutionContext): Future[Done]

}
