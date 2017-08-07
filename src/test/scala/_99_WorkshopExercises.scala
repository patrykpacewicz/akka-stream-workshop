import akka.NotUsed
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._
import org.scalatest.Matchers

import scala.concurrent.duration._

class _99_WorkshopExercises extends WorkshopSpec with Matchers {
  import WorkshopSpec.Implicits._

  implicit val system = ActorSystem("06-Graphs")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  "skip every 3th" should {
    "exercise" in {
      // given
      val in = 1 to 10
      val flow: Flow[Int, Int, NotUsed] = ???

      // when
      val result: Int = Source(in)
        .via(flow)
        .map(viaLogger0)
        .runReduce(_ + _)
        .awaitOnCompleteAndLog()

      // then
      result should equal(37)
    }
  }

  "remove duplicates in stream" should {
    "exercise" in {
      // given
      val in = List(4, 4, 3, 3, 2, 1, 4, 3, 2, 4, 5)
      val flow: Flow[Int, Int, NotUsed] = ???

      // when
      val result: List[Int] = Source(in)
        .via(flow)
        .map(viaLogger0)
        .runWith(Sink.seq)
        .awaitOnCompleteAndLog().toList

      // then
      result should equal(List(4, 3, 2, 1, 5))
    }
  }

  "sends avg from last 3 elements" should {
    "exercise" in {
      // given
      val in = List[Float](1,1, 2,2, 4,4)
      val flow: Flow[Float, Float, NotUsed] = ???

      // when
      val result: List[Float] = Source(in)
        .via(flow)
        .map(viaLogger0)
        .runWith(Sink.seq)
        .awaitOnCompleteAndLog().toList

      // then
      result should equal(List[Float](1F, 1F, 1.5F, 2F, 3F, 4F))
    }
  }

  "change stream of [Char] to stream of words [String] (separated by whitespaces)" should {
    "exercise" in {
      // given
      val in1 = List[Char]('T', 'h', 'e', ' ', 't', 'h', 'r', 'e', 'e', ' ', 'w', 'o', 'r', 'd', 's', ' ', '!')
      val in2 = List[Char](' ', 'T', 'h', 'e', ' ', 't', 'h', 'r', 'e', 'e', ' ', 'w', 'o', 'r', 'd', 's', ' ', '!')
      val in3 = List[Char]('T', 'h', 'e', ' ', ' ', 't', 'h', 'r', 'e', 'e', ' ', 'w', 'o', 'r', 'd', 's', ' ', '!')
      val in4 = List[Char]('T', 'h', 'e', ' ', 't', 'h', 'r', 'e', 'e', ' ', 'w', 'o', 'r', 'd', 's', ' ')

      val flow: Flow[Char, String, NotUsed] = ???

      // when
      def result(in: List[Char]): List[String] = Source(in)
        .via(flow)
        .map(viaLogger0)
        .runWith(Sink.seq)
        .awaitOnCompleteAndLog().toList

      // then
      result(in1) should equal(List[String]("The", "three", "words", "!"))
      result(in2) should equal(List[String]("The", "three", "words", "!"))
      result(in3) should equal(List[String]("The", "three", "words", "!"))
      result(in4) should equal(List[String]("The", "three", "words", "!"))
    }
  }

  "parallel task in flow" should {
    def longAction(i: Int) = {
      logger.info(s"[$i] Long Action Start")
      Thread.sleep(400)
      logger.info(s"[$i] Long Action End")
      i
    }

    "exercise" in {
      val flow = Flow[Int].map(longAction) // ???

      Source(1 to 10)
        .via(flow)
        .runForeach(toLogger0)
        .awaitOnCompleteAndLog(timeout = 1.second)
    }

    "example: mapAsync" in {
      import scala.concurrent.Future
      val flow = Flow[Int].mapAsync(10)(i => Future(longAction(i))(executionContext))

      Source(1 to 10)
        .via(flow)
        .runForeach(toLogger0)
        .awaitOnCompleteAndLog(timeout = 1.second)
    }
  }

  "create flow that wrap other flow and return: Flow[In, (In, Out), Mat]" should {
    "exercise" in { ??? }
  }

  "create flow with retry functionality " should {
    "exercise" in { ??? }
  }
}
