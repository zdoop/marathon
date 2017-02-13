package mesosphere.marathon.core.instance.update

import akka.Done
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.core.condition.Condition
import mesosphere.marathon.core.event.MarathonEvent
import mesosphere.marathon.core.instance.Instance.InstanceState
import mesosphere.marathon.core.instance.Instance
import mesosphere.marathon.state.{ PathId, Timestamp }

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * A consumer interested in instance change events.
  *
  * [[InstanceChange]]s will be processed in order sequentially by the
  * [[mesosphere.marathon.core.task.tracker.TaskStateOpProcessor]] for every change
  * after the change has been persisted.
  */
trait InstanceChangeHandler extends StrictLogging {
  def name: String
  def process(update: InstanceChange): Future[Done]

  def continueOnError(name: String, update: InstanceChange)(handle: (InstanceChange) => Future[Done]): Future[Done] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val maybeProcessed: Option[Future[Done]] = Option(handle(update))
    maybeProcessed match {
      case Some(processed) =>
        processed.recover {
          case NonFatal(e) =>
            logger.error(s"while executing step $name for [${update.id.idString}], continue with other steps", e)
            Done
        }
      case None =>
        logger.error(s"step $name for [${update.id.idString}] returned null, continue with other steps")
        Future.successful(Done)
    }
  }
}

/**
  * An event notifying of an [[Instance]] change.
  */
sealed trait InstanceChange extends Product with Serializable {
  /** The affected [[Instance]] */
  val instance: Instance
  /** Id of the affected [[Instance]] */
  val id: Instance.Id = instance.instanceId
  /** version of the related run spec */
  val runSpecVersion: Timestamp = instance.runSpecVersion
  /** Condition of the [[Instance]] */
  val condition: Condition = instance.state.condition
  /** Id of the related [[mesosphere.marathon.state.RunSpec]] */
  val runSpecId: PathId = id.runSpecId
  /** the previous state of this instance */
  def lastState: Option[InstanceState]
  /** Events that should be published for this change */
  def events: Seq[MarathonEvent]
}

/** The given instance has been created or updated. */
case class InstanceUpdated(
  instance: Instance,
  lastState: Option[InstanceState],
  events: Seq[MarathonEvent]) extends InstanceChange

/** The given instance has been deleted. */
case class InstanceDeleted(
  instance: Instance,
  lastState: Option[InstanceState],
  events: Seq[MarathonEvent]) extends InstanceChange
