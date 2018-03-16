// Copyright (C) 2018 the original author or authors.
// See the LICENSE file distributed with this work for additional
// information regarding copyright ownership.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package io.cafienne.bounded.cargosample.domain

import akka.actor._
import io.cafienne.bounded.aggregate._
import io.cafienne.bounded.cargosample.domain.Cargo.CargoAggregateState
import io.cafienne.bounded.cargosample.domain.CargoDomainProtocol._

import scala.collection.immutable.Seq
import scala.reflect.ClassTag

/**
 * Aggregate root that keeps the logic of the cargo.
 * @param cargoId unique identifier for cargo.
 */
class Cargo(cargoId: AggregateRootId) extends AggregateRootActor with AggregateStateCreator with ActorLogging {

  override def aggregateId: AggregateRootId = cargoId

  override def handleCommand(command: DomainCommand, currentState: AggregateState): Reply = {
    command match {
      case cmd: PlanCargo => Ok(Seq(CargoPlanned(cmd.metaData, cmd.cargoId, cmd.trackingId, cmd.routeSpecification)))
      case cmd: SpecifyNewRoute => Ok(Seq(NewRouteSpecified(cmd.metaData, cmd.cargoId, cmd.routeSpecification)))
      case other => Ko(new UnexpectedCommand(other))
    }
  }

  override def newState(evt: DomainEvent): AggregateState = {
    evt match {
      case evt: CargoPlanned => new CargoAggregateState(evt.trackingId, evt.routeSpecification)
      case _ => throw new IllegalArgumentException(s"Event $evt is not valid to create a new CargoAggregateState")
    }
  }

}

object Cargo extends AggregateRootCreator {

  case class CargoAggregateState(trackingId: TrackingId, routeSpecification: RouteSpecification) extends AggregateState {
    override def update(evt: DomainEvent): AggregateState = {
      evt match {
        case CargoPlanned(meta, cargoId, trackingId, routeSpecification) => CargoAggregateState(trackingId, routeSpecification)
        case NewRouteSpecified(meta, cargoId, routeSpecification) => this.copy(routeSpecification = routeSpecification)
        case other => this
      }
    }
  }

  def props(cargoId: AggregateRootId): Props = Props(classOf[Cargo], cargoId)

  final val aggregateRootTag = "ar-cargo" // used to tag the events and read them

  override def create[A <: AggregateRootActor :ClassTag](id: AggregateRootId): A = new Cargo(id).asInstanceOf[A]

}

