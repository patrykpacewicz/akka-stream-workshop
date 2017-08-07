import akka.Done
import akka.actor._
import akka.event.Logging
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream._

import scala.concurrent.{Await, Future, TimeoutException}

class _04_ErrorHandling extends WorkshopSpec {
  import WorkshopSpec.Implicits._

  implicit val system = ActorSystem("04-ErrorHandling")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  "Simple fail" should {
    "fromFuture: FAIL" in {
      // given
      val throwable = new Exception("fromFuture.fail")

      // when
      val result: Future[Done] = Source
        .fromFuture(Future.failed(throwable))
        .runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "failed" in {
      // given
      val throwable = new Exception("failed.fail")

      // when
      val result: Future[Done] = Source
        .failed(throwable)
        .runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "mapError" in {
      Source(List(1, 0, 2))
        .map(1 / _)
        .mapError {
          case ex: ArithmeticException => new IllegalArgumentException("error-caught")
        }
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }
  }

  "Failure recovery" should {
    "recover" in {
      Source(List(1, 0, 2))
        .map(1 / _)
        .recover {
          case ex: ArithmeticException => 333
          //          case ex: ArithmeticException => ex
          //          case ex: ArithmeticException => throw ex // log error
        }
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "recoverWithRetries" in {
      Source(List(1, 0, 2))
        .map(1 / _)
        .recoverWithRetries(2, { // -1, 0, 1, 2
          case ex: ArithmeticException => Source(List(10, 0, 20)).map(100 / _)
        })
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()

    }
  }

  "Supervision strategies" should {
    "successful flow" in {
      // given
      val list: List[Int] = List(1, 2, 3, 0, 9, 8, 7)

      // when
      val result: Future[Int] = Source(list)
        .map(viaLogger1)
        .fold(0)( (a, i) => a + i)
        .runWith(Sink.head)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "stop as default strategy" in {
      // given
      val list: List[Int] = List(1, 2, 3, 0, 9, 8, 7)

      // when
      val result: Future[Int] = Source(list)
        .map(viaLogger1)
        .fold(0)( (a, i) => a + (i / i * i))
        .runWith(Sink.head)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "add decider strategy" in {
      // given
      val list: List[Int] = List(1, 2, 3, 0, 9, 8, 7)

      val decider: Supervision.Decider = {
        case ex: ArithmeticException =>
          logger.warn(s"ArithmeticException: ${ex.getMessage}")
          Supervision.Stop
//          Supervision.Resume
//          Supervision.Restart
        case _ => Supervision.Stop
      }

      // when
      val result: Future[Int] = Source(list)
        .map(viaLogger1)
        .fold(0)( (a, i) => a + (i / i * i))
        .withAttributes(ActorAttributes.supervisionStrategy(decider))
        .runWith(Sink.head)

      // wait
      result.awaitOnCompleteAndLog()
    }
  }
}
