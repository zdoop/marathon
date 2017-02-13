package mesosphere.marathon.core.task.tracker

import akka.actor.ActorRef
import akka.stream.Materializer
import mesosphere.marathon.core.base.Clock
import mesosphere.marathon.core.instance.update.InstanceUpdateOpResolver
import mesosphere.marathon.core.leadership.LeadershipModule
import mesosphere.marathon.core.scheduling.SchedulingModule
import mesosphere.marathon.core.scheduling.behavior.InstanceChangeBehavior
import mesosphere.marathon.core.task.tracker.impl._
import mesosphere.marathon.metrics.Metrics
import mesosphere.marathon.storage.repository.InstanceRepository

/**
  * Provides the interfaces to query the current task state ([[InstanceTracker]]) and to
  * update the task state ([[TaskStateOpProcessor]]).
  */
class InstanceTrackerModule(
    clock: Clock,
    metrics: Metrics,
    config: InstanceTrackerConfig,
    schedulingModule: SchedulingModule,
    leadershipModule: LeadershipModule,
    instanceRepository: InstanceRepository)(implicit mat: Materializer) {
  lazy val instanceTracker: InstanceTracker =
    new InstanceTrackerDelegate(Some(metrics), config, instanceTrackerActorRef)
  lazy val instanceChangeBehavior: InstanceChangeBehavior = schedulingModule.instanceChangeBehavior

  def instanceCreationHandler: InstanceCreationHandler = instanceStateOpProcessor
  def stateOpProcessor: TaskStateOpProcessor = instanceStateOpProcessor

  private[this] def updateOpResolver(instanceTrackerRef: ActorRef): InstanceUpdateOpResolver =
    new InstanceUpdateOpResolver(
      new InstanceTrackerDelegate(None, config, instanceTrackerRef), clock)
  private[this] def instanceOpProcessor(instanceTrackerRef: ActorRef): InstanceOpProcessor =
    new InstanceOpProcessorImpl(instanceTrackerRef, instanceRepository, updateOpResolver(instanceTrackerRef), config)
  private[this] lazy val instanceUpdaterActorMetrics = new InstanceUpdateActor.ActorMetrics(metrics)
  private[this] def instanceUpdaterActorProps(instanceTrackerRef: ActorRef) =
    InstanceUpdateActor.props(clock, instanceUpdaterActorMetrics, instanceOpProcessor(instanceTrackerRef))
  private[this] lazy val instancesLoader = new InstancesLoaderImpl(instanceRepository)
  private[this] lazy val instanceTrackerMetrics = new InstanceTrackerActor.ActorMetrics(metrics)
  private[this] lazy val instanceTrackerActorProps = InstanceTrackerActor.props(
    instanceTrackerMetrics, instancesLoader, instanceChangeBehavior, instanceUpdaterActorProps)
  protected lazy val instanceTrackerActorName = "instanceTracker"
  private[this] lazy val instanceTrackerActorRef = leadershipModule.startWhenLeader(
    instanceTrackerActorProps, instanceTrackerActorName
  )
  private[this] lazy val instanceStateOpProcessor =
    new InstanceCreationHandlerAndUpdaterDelegate(clock, config, instanceTrackerActorRef)
}
