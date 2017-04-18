package mesosphere.marathon
package api

import mesosphere.marathon.MarathonConf
import mesosphere.marathon.raml.{ NetworkMode, Pod }

object PodNormalization {

  // If we change/add/upgrade the notion of a Pod and can't do it purely in the internal model,
  // update the json first
  def normalize(pod: Pod, marathonConfig: MarathonConf): Pod = {
    if (pod.networks.exists(_.name.isEmpty)) {
      val networks = pod.networks.map { network =>
        if (network.mode == NetworkMode.Container && network.name.isEmpty) {
          marathonConfig.defaultNetworkName.get.fold(network) { name =>
            network.copy(name = Some(name))
          }
        } else {
          network
        }
      }
      pod.copy(networks = networks)
    } else {
      pod
    }
  }
}
