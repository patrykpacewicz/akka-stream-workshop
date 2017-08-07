import akka.Done
import akka.actor._
import akka.event.Logging
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream._

import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.duration._

class _05_BufferAndBackpressure  extends WorkshopSpec {

  import WorkshopSpec.Implicits._

  implicit val system = ActorSystem("04-ErrorHandling")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  "Backpressure" should {
    "throttle" in {
      Source(1 to 10)
        .map(viaLogger1)
        .throttle(2, 1.seconds, 1, ThrottleMode.Shaping)
//        .throttle(2, 1.seconds, 3, ThrottleMode.Shaping)
//        .throttle(2, 1.seconds, 20, ThrottleMode.Shaping)
//        .throttle(2, 1.seconds, 3, ThrottleMode.Enforcing)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "bucket maximumBurst" in {
      Source(1 to 4)
        .map(viaLogger1)
        .throttle(1, 1000.millis, 0, ThrottleMode.Shaping)
        .mapConcat(i => 101 to 110)
        .throttle(1, 200.millis, 5, ThrottleMode.Shaping)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "groupedWithin" in {
      Source(1 to 10)
        .map(viaLogger1)
        .throttle(1, 200.milliseconds, 0, ThrottleMode.Shaping)
        .groupedWithin(10, 1.second)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "conflate" in {
      Source(1L to 14L)
        .throttle(1, 100.milliseconds, 0, ThrottleMode.Shaping)
        .map(viaLogger1)
        .conflate{ (o1, o2) => (o1.toString + o2.toString).toLong }
        .throttle(1, 500.milliseconds, 0, ThrottleMode.Shaping)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "batch" in {
      Source(1L to 14L)
        .throttle(1, 100.milliseconds, 0, ThrottleMode.Shaping)
        .map(viaLogger1)
        .batch(3, i => List(i)){ (b, el) => b :+ el}
        .throttle(1, 500.milliseconds, 0, ThrottleMode.Shaping)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "expand" in {
      Source(1 to 5)
        .throttle(1, 1000.milliseconds, 0, ThrottleMode.Shaping)
        .map(viaLogger1)
        .expand(i => List(i, i*10, i*100, i*1000).toIterator)
        .throttle(1, 100.milliseconds, 0, ThrottleMode.Shaping)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()

    }
  }

  "InputBuffer" should {
    "simple example" in {
      Source(1 to 4)
        .map(viaLogger1).async
        .map(viaLogger2).async
        .map(viaLogger3).async
        .runWith(Sink.ignore)
        .awaitOnCompleteAndLog()
    }

    "simple example 2" in {
//      val inputBuffer = Attributes.inputBuffer(initial = 1, max = 1)
//      val inputBuffer = Attributes.inputBuffer(initial = 32, max = 32)

      val flow1 = Flow[Int].map(viaLogger1)
      val flow2 = Flow[Int].map(viaLogger2).delay(1.seconds, DelayOverflowStrategy.backpressure)
//        .async
//        .addAttributes(inputBuffer)
      val flow3 = Flow[Int].map(viaLogger3)

      Source(1 to 10)
        .via(flow1)
        .via(flow2)
        .via(flow3)
        .runWith(Sink.ignore)
        .awaitOnCompleteAndLog()
    }
  }

  "Buffers" should {
    "set buffer" in {
      Source(1 to 20)
        .map(viaLogger1)
        //        .buffer(5, OverflowStrategy.backpressure)
        //        .buffer(5, OverflowStrategy.dropNew)
        //        .buffer(5, OverflowStrategy.dropTail)
        //        .buffer(5, OverflowStrategy.dropHead)
        //        .buffer(9, OverflowStrategy.dropBuffer)
        .buffer(5, OverflowStrategy.fail)
        .throttle(2, 1.seconds, 5, ThrottleMode.Shaping)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "set buffer 2" in {
      Source(1 to 10)
        .map(viaLogger1)
        .throttle(1, 100.millis, 0, ThrottleMode.shaping)
        .buffer(5, OverflowStrategy.dropTail)
        .map(viaLogger2)
        .throttle(1, 300.millis, 0, ThrottleMode.shaping)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }
  }
}