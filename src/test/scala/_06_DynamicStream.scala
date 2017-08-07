import akka.{Done, NotUsed}
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.scalatest.Matchers

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._


class _06_DynamicStream extends WorkshopSpec with Matchers {
  import WorkshopSpec.Implicits._

  implicit val system = ActorSystem("07-DynamicStream")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  "DynamicStream" should {
    "KillSwitch" in {
      val (killSwitch, result) = Source(1 to 20)
        .throttle(1, 100.millis, 0, ThrottleMode.Shaping)
        .viaMat(KillSwitches.single)(Keep.right)
//        .viaMat(KillSwitches.shared("name").flow)(Keep.right)
        .toMat(Sink.foreach(toLogger0))(Keep.both)
        .run()

      result.logOnComplete()

      Thread.sleep(1000)

//      killSwitch.shutdown()
      killSwitch.abort(new Exception("kill-switch"))
      result.await()
    }

    "MergeHub" in {
      val graph: RunnableGraph[Sink[String, NotUsed]] = MergeHub
        .source[String]
        .to(Sink.foreach(toLogger0))

      val toConsumer: Sink[String, NotUsed] = graph.run()

      // Feeding two independent sources into the hub.
      Source.single("Hello ").map(viaLogger1).runWith(toConsumer)
      Source.single("World!").map(viaLogger2).runWith(toConsumer)
    }

    "BroadcastHub" in {
      val graph: RunnableGraph[Source[String, NotUsed]] = Source
        .tick(0.second, 500.millis, "Msg!")
        .toMat(BroadcastHub.sink)(Keep.right)

      val fromProducer: Source[String, NotUsed] = graph.run()

      // Print out messages from the producer in two independent consumers
      Thread.sleep(2000)
      fromProducer.runForeach(toLogger1)
      Thread.sleep(2000)
      fromProducer.runForeach(toLogger2)
      Thread.sleep(2000)
    }

    "Publish-Subscribe service" in {
      // Obtain a Sink and Source which will publish and receive from the "bus" respectively.
      val (sink, source) = MergeHub
        .source[String]
        .toMat(BroadcastHub.sink)(Keep.both)
        .run()

      source.runWith(Sink.ignore)

      // We create now a Flow that represents a publish-subscribe channel using the above
      // started stream as its "topic". We add two more features, external cancellation of
      // the registration and automatic cleanup for very slow subscribers.
      val busFlow: Flow[String, String, UniqueKillSwitch] =
      Flow.fromSinkAndSource(sink, source)
        .joinMat(KillSwitches.singleBidi[String, String])(Keep.right)
        .backpressureTimeout(2.seconds)

      val sendAndReciveSwitch: UniqueKillSwitch =
        Source(1 to 10).map(_.toString)
          .throttle(1, 300.millis, 0, ThrottleMode.Shaping)
          .map(viaLogger1)
          .viaMat(busFlow)(Keep.right)
          .to(Sink.foreach(toLogger1))
          .run()

      val onlySendSwitch: UniqueKillSwitch =
        Source(10 to 100).map(_.toString)
          .throttle(1, 100.millis, 0, ThrottleMode.Shaping)
          .map(viaLogger2)
          .viaMat(busFlow)(Keep.right)
          .to(Sink.ignore)
          .run()

      val onlyReciveSwitch: UniqueKillSwitch =
        Source.empty
          .viaMat(busFlow)(Keep.right)
          .to(Sink.foreach(toLogger3))
          .run()


      // Shut down externally
      Thread.sleep(1000)
      onlyReciveSwitch.shutdown()
      Thread.sleep(1000)
      onlySendSwitch.shutdown()
      Thread.sleep(1000)
      sendAndReciveSwitch.shutdown()
    }
  }
}
