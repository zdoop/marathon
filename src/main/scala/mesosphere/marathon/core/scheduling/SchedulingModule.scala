package mesosphere.marathon
package core.scheduling

import com.google.inject.Inject
import mesosphere.marathon.core.scheduling.behavior.InstanceChangeBehavior
import mesosphere.marathon.core.scheduling.behavior.impl.{ ContinuousBehavior, InstanceChangeBehaviorImpl }
import mesosphere.marathon.core.task.update.impl.steps._
import mesosphere.marathon.metrics.Metrics

/**
  * Defines the behaviors to apply upon instance state changes.
  */
trait SchedulingModule {
  def instanceChangeBehavior: InstanceChangeBehavior
}

private[core] class SchedulingModuleImpl(
    instanceChangeBehaviorSteps: InstanceChangeBehaviorSteps, metrics: Metrics) extends SchedulingModule {
  override lazy val instanceChangeBehavior: InstanceChangeBehavior = {
    val continuousBehavior = {
      // This is a sequence on purpose. The specified steps are executed in order for every
      // task status update that leads to a persisted instance state change.
      // This way we make sure that e.g. the InstanceTracker already reflects the changes for the update
      // before we notify the launch queue (notifyLaunchQueueStepImpl).
      val continuousUpdateSteps = Seq(
        instanceChangeBehaviorSteps.notifyHealthCheckManagerStepImpl,
        instanceChangeBehaviorSteps.notifyRateLimiterStepImpl,
        instanceChangeBehaviorSteps.notifyLaunchQueueStepImpl,
        instanceChangeBehaviorSteps.taskStatusEmitterPublishImpl,
        instanceChangeBehaviorSteps.postToEventStreamStepImpl,
        instanceChangeBehaviorSteps.scaleAppUpdateStepImpl
      )
      new ContinuousBehavior(continuousUpdateSteps, metrics)
    }
    new InstanceChangeBehaviorImpl(continuousBehavior)
  }
}

/**
  * Used for wiring steps that are relevant for handling instance state changes.
  */
private[core] class InstanceChangeBehaviorSteps @Inject() (
  val notifyHealthCheckManagerStepImpl: NotifyHealthCheckManagerStepImpl,
  val notifyRateLimiterStepImpl: NotifyRateLimiterStepImpl,
  val notifyLaunchQueueStepImpl: NotifyLaunchQueueStepImpl,
  val taskStatusEmitterPublishImpl: TaskStatusEmitterPublishStepImpl,
  val postToEventStreamStepImpl: PostToEventStreamStepImpl,
  val scaleAppUpdateStepImpl: ScaleAppUpdateStepImpl
)
