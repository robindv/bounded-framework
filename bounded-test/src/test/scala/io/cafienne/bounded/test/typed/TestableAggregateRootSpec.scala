/*
 * Copyright (C) 2016-2020 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.test.typed

import java.time.{OffsetDateTime, ZonedDateTime}

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, LogCapturing, ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.util.Timeout
import io.cafienne.bounded.aggregate.typed.TypedAggregateRootManager
import io.cafienne.bounded.{BuildInfo, RuntimeInfo}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpecLike
import akka.actor.typed.scaladsl.adapter._
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import com.typesafe.config.{ConfigFactory, ConfigValue}
import io.cafienne.bounded.test.DomainProtocol.StateTransitionForbidden
import io.cafienne.bounded.test.typed.TestableAggregateRoot.{
  AggregateThrownException,
  MisdirectedCommand,
  NoCommandsIssued,
  UnexpectedCommandHandlingSuccess
}

import scala.concurrent.duration._

class TestableAggregateRootSpec
    extends ScalaTestWithActorTestKit(
      PersistenceTestKitPlugin.config
        .withFallback(ConfigFactory.parseString("akka.actor.allow-java-serialization=true"))
        .withFallback(ConfigFactory.defaultApplication())
    )
    with AsyncFlatSpecLike
    with ScalaFutures
    with BeforeAndAfterAll { //with LogCapturing {

  import TypedSimpleAggregate._
  implicit val classicActorSystem = system.toClassic
  implicit val typedTestKit       = testKit
  val persistenceTestKit          = PersistenceTestKit(system)

  behavior of "Testable Typed Aggregate Root"

  implicit val gatewayTimeout = Timeout(10.seconds)
  implicit val buildInfo      = BuildInfo("spec", "1.0")
  implicit val runtimeInfo    = RuntimeInfo("current")
  implicit val actorSytem     = system

  val creator: TypedAggregateRootManager[SimpleAggregateCommand] = new SimpleAggregateManager()

//  override def beforeEach(): Unit = {
//    persistenceTestKit.clearAll()
//  }

  "TestableAggregateRoot" should "be created with initial events" in {
    val commandMetaData = AggregateCommandMetaData(OffsetDateTime.now(), None)
    val eventMetaData   = TestMetaData.fromCommand(commandMetaData)(buildInfo, runtimeInfo)
    val aggregateId     = "ar0"

    val testProbe = TestProbe[Response]()
    val testAR = TestableAggregateRoot
      .given(
        creator,
        aggregateId,
        Created(aggregateId, eventMetaData),
        ItemAdded(aggregateId, eventMetaData, "ar0 item 1")
      )
      .when(AddItem(aggregateId, commandMetaData, "ar0 item 2", testProbe.ref))

    testProbe.expectMessage(OK)

    testAR.events should be(List(ItemAdded(aggregateId, eventMetaData, "ar0 item 2")))

  }

  it should "provide command handling failure for assertions" in {
    val commandMetaData      = AggregateCommandMetaData(OffsetDateTime.now(), None)
    val eventMetaData        = TestMetaData.fromCommand(commandMetaData)(buildInfo, runtimeInfo)
    val testAggregateRootId1 = "3"
    val testProbe            = TestProbe[Response]()

    TestableAggregateRoot
      .given(
        creator,
        testAggregateRootId1,
        Created(testAggregateRootId1, eventMetaData)
      )
      .when(TriggerError(testAggregateRootId1, commandMetaData, testProbe.ref))
      .failure match {
      case AggregateThrownException(ex: IllegalArgumentException) =>
        ex.getMessage should be("This is an exception thrown during processing the command for AR 3")
      case other => fail("not expected: " + other)
    }
  }

  it should "prevent handling of commands that target another aggregate" in {
    val commandMetaData = AggregateCommandMetaData(OffsetDateTime.now(), None)
    val aggregateRootId = "4"
    val wrongId         = "5"
    val testProbe       = TestProbe[Response]()

    an[MisdirectedCommand] should be thrownBy {
      TestableAggregateRoot
        .given(creator, aggregateRootId)
        .when(Create(wrongId, commandMetaData, testProbe.ref))
    }
  }

  it should "signal that handling was successful despite expected failure" in {
    val commandMetaData = AggregateCommandMetaData(OffsetDateTime.now(), None)
    val eventMetaData   = TestMetaData.fromCommand(commandMetaData)(buildInfo, runtimeInfo)

    val aggregateRootId = "3"
    val testProbe       = TestProbe[Response]()

    an[UnexpectedCommandHandlingSuccess] should be thrownBy {
      TestableAggregateRoot
        .given(
          creator,
          aggregateRootId,
          Created(aggregateRootId, eventMetaData)
        )
        .when(AddItem(aggregateRootId, commandMetaData, "new item", testProbe.ref))
        .failure
    }
    //TODO ... strange ?
//    testProbe.expectMessage(OK) should be(OK)

  }

  it should "tell that no point expecting failure when no commands were issued" in {
    val commandMetaData = AggregateCommandMetaData(OffsetDateTime.now(), None)
    val eventMetaData   = TestMetaData.fromCommand(commandMetaData)(buildInfo, runtimeInfo)
    val aggregateRootId = "3"

    an[NoCommandsIssued.type] should be thrownBy {
      TestableAggregateRoot
        .given(
          creator,
          aggregateRootId,
          Created(aggregateRootId, eventMetaData)
        )
        .failure
    }
  }

  it should "tell that no point expecting events when no commands were issued" in {
    val commandMetaData = AggregateCommandMetaData(OffsetDateTime.now(), None)
    val eventMetaData   = TestMetaData.fromCommand(commandMetaData)(buildInfo, runtimeInfo)
    val aggregateRootId = "3"

    an[NoCommandsIssued.type] should be thrownBy {
      TestableAggregateRoot
        .given(
          creator,
          aggregateRootId,
          Created(aggregateRootId, eventMetaData)
        )
        .events
    }
  }

//  "give a clear failure when a command is processed that has no handler" in {
//    val aggregateRootId = "4"
//
//    an[CommandHandlingFailed] should be thrownBy {
//      val ar = TestableAggregateRoot
//        .given[TestAggregateRoot, TestAggregateRootState](
//          testAggregateRootCreator,
//          aggregateRootId,
//          InitialStateCreated(currentMeta, aggregateRootId, "new")
//        )
//        .when(CommandWithoutHandler(metaData, aggregateRootId, "this one fails"))
//
//      ar.events should be("throwing an CommandHandlingFailed")
//    }
//
//  }

  protected override def afterAll(): Unit = {
    ActorTestKit.shutdown(system, 30.seconds, throwIfShutdownFails = true)
  }

}
