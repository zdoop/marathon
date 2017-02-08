package mesosphere.marathon.state.schedule

/**
  * A [[SchedulingStrategy]] defines how, if, and when instances of a [[mesosphere.marathon.state.RunSpec]] are
  * scheduled to be launched.
  */
sealed trait SchedulingStrategy

/**
  * A [[Continuous]] [[SchedulingStrategy]] has the goal to continuously have a designated amount of instances running.
  * Whenever an instance terminates or is not considered active anymore, this strategy will schedule a new instance as a
  * replacement. Note that the decision of when an instance is considered inactive due to being unreachable for too long
  * is covered in the according [[mesosphere.marathon.state.UnreachableStrategy]].
  */
case object Continuous extends SchedulingStrategy
