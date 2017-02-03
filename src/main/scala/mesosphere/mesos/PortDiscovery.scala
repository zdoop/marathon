package mesosphere.mesos

import mesosphere.marathon.api.serialization.{ PortDefinitionSerializer, PortMappingSerializer }
import mesosphere.marathon.raml.Endpoint
import mesosphere.marathon.state.{ AppDefinition, DiscoveryInfo, IpAddress }
import org.apache.mesos.Protos.Port

import scala.collection.immutable.Seq

trait PortDiscovery {

  def generate(hostModeNetworking: Boolean, allocations: Map[Endpoint, Option[Int]]): Seq[Port] = {
    if (!hostModeNetworking) {
      // The run spec uses bridge and user modes with portMappings, use them to create the Port messages
      val ports: Seq[Seq[Port]] = allocations.collect {
        case (ep, None) =>
          // No host port has been defined. See PortsMatcher.mappedPortRanges, use container port instead.
          val updatedEp =
            ep.copy(labels = ep.labels + NetworkScope.Container.discovery)
          val containerPort: Int = ep.containerPort.getOrElse(throw new IllegalStateException(
            "expected non-empty container port in conjunction with non-host networking"
          ))
          PortMappingSerializer.toMesosPorts(updatedEp, containerPort)
        case (ep, Some(hostPort)) =>
          val updatedEp = ep.copy(labels = ep.labels + NetworkScope.Host.discovery)
          PortMappingSerializer.toMesosPorts(updatedEp, hostPort)
      }(collection.breakOut)
      ports.flatten
    } else {
      // The port numbers are the allocated ports, we need to overwrite them the port numbers assigned to this particular task.
      // network-scope is assumed to be host, no need for an additional scope label here.
      val ports: Seq[Seq[Port]] = allocations.collect {
        case (ep, Some(hostPort)) =>
          PortMappingSerializer.toMesosPorts(ep, hostPort)
        case (ep, None) =>
          // should be an allocated hostPort for every endpoint when in HostNetwork mode
          throw new IllegalStateException(s"host-port not allocated for endpoint ${ep.name}")
      }(collection.breakOut)
      ports.flatten
    }
  }

  def generate(runSpec: AppDefinition, hostPorts: Seq[Option[Int]]): Seq[Port] =
    runSpec.ipAddress match {
      case Some(IpAddress(_, _, DiscoveryInfo(ports), _)) if ports.nonEmpty => ports.map(_.toProto)
      case _ =>
        runSpec.container.withFilter(_.portMappings.nonEmpty).map { c =>
          // The run spec uses bridge and user modes with portMappings, use them to create the Port messages
          c.portMappings.zip(hostPorts).collect {
            case (portMapping, None) =>
              // No host port has been defined. See PortsMatcher.mappedPortRanges, use container port instead.
              val updatedPortMapping =
                portMapping.copy(labels = portMapping.labels + NetworkScope.Container.discovery)
              PortMappingSerializer.toMesosPort(updatedPortMapping, portMapping.containerPort)
            case (portMapping, Some(hostPort)) =>
              val updatedPortMapping = portMapping.copy(labels = portMapping.labels + NetworkScope.Host.discovery)
              PortMappingSerializer.toMesosPort(updatedPortMapping, hostPort)
          }
        }.getOrElse(
          // Serialize runSpec.portDefinitions to protos. The port numbers are the service ports, we need to
          // overwrite them the port numbers assigned to this particular task.
          // network-scope is assumed to be host, no need for an additional scope label here.
          runSpec.portDefinitions.zip(hostPorts).collect {
          case (portDefinition, Some(hostPort)) =>
            PortDefinitionSerializer.toMesosProto(portDefinition).map(_.toBuilder.setNumber(hostPort).build)
        }.flatten
        )
    }
}

object PortDiscovery extends PortDiscovery
