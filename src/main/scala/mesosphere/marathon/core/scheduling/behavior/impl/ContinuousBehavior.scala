package mesosphere.marathon
package core.scheduling.behavior
package impl

import akka.Done
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.core.instance.update.{ InstanceChange, InstanceChangeHandler }
import mesosphere.marathon.metrics.Metrics.Timer
import mesosphere.marathon.metrics.{ MetricPrefixes, Metrics }

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Performs the steps necessary when instances of continuously scheduled run specs change.
  */
private[scheduling] class ContinuousBehavior(
    steps: Seq[InstanceChangeHandler],
    metrics: Metrics) extends InstanceChangeBehavior with StrictLogging {

  private[this] val stepTimers: Map[String, Timer] = steps.map { step =>
    step.name -> metrics.timer(metrics.name(MetricPrefixes.SERVICE, getClass, s"step-${step.name}"))
  }(collection.breakOut)

  logger.info(
    "Initialized ContinuousBehavior with steps:\n{}",
    steps.map(step => s"* ${step.name}").mkString("\n"))

  override def handle(change: InstanceChange)(implicit ec: ExecutionContext): Future[Done] = {
    steps.foldLeft(Future.successful(Done)) { (resultSoFar, nextStep) =>
      resultSoFar.flatMap { _ =>
        stepTimers(nextStep.name).timeFuture {
          logger.debug(s"Executing ${nextStep.name} for [${change.instance.instanceId}]")
          nextStep.process(change).map { _ =>
            logger.debug(s"Done with executing ${nextStep.name} for [${change.instance.instanceId}]")
            Done
          }
        }
      }
    }
  }

}
