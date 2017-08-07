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

    "example: 3 outputs and only 2 merge" in {
      // given
      val in = 1 to 10
      val flow: Flow[Int, Int, NotUsed]  = Flow.fromGraph(GraphDSL.create(){ implicit builder =>
        import GraphDSL.Implicits._

        val bcast = builder.add(Balance[Int](3))
        val merge = builder.add(Merge[Int](2))

        bcast ~> merge
        bcast ~> merge
        bcast ~> Sink.ignore

        FlowShape(bcast.in, merge.out)
      })

      // when
      val result: Int = Source(in)
        .via(flow)
        .map(viaLogger0)
        .runReduce(_ + _)
        .awaitOnCompleteAndLog()

      // then
      result should equal(37)
    }

    "example: statefulMapConcat" in {
      // given
      val in = 1 to 10
      val flow: Flow[Int, Int, NotUsed] = Flow[Int]
        .statefulMapConcat( () => {
          var i = 0

          (el) => {
            i = (i+1) % 3
            if (i == 0) List()
            else List(el)
          }
        })

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

    "example: statefulMapConcat" in {
      // given
      val in = List(4, 4, 3, 3, 2, 1, 4, 3, 2, 4, 5)
      val flow: Flow[Int, Int, NotUsed] = Flow[Int].statefulMapConcat(() => {
        var history = Map[Int, Boolean]()

        (el) => {
          if (history.getOrElse(el, false)) List()
          else {
            history = history + (el -> true)
            List(el)
          }
        }
      })

      // when
      val result: List[Int] = Source(in)
        .via(flow)
        .map(viaLogger0)
        .runWith(Sink.seq)
        .awaitOnCompleteAndLog().toList

      // then
      result should equal(List(4, 3, 2, 1, 5))
    }

    "example: custom GraphStage with state" in {
      // given
      val in = List(4, 4, 3, 3, 2, 1, 4, 3, 2, 4, 5)
      val flow: Flow[Int, Int, NotUsed] = Flow.fromGraph(new GraphStage[FlowShape[Int, Int]] {
        val in = Inlet[Int]("UniqStage.in")
        val out = Outlet[Int]("UniqStage.out")
        val shape = FlowShape.of(in, out)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
          var history = Map[Int, Boolean]()

          setHandler(in, new InHandler {
            override def onPush(): Unit = {
              val elem = grab(in)
              if (history.getOrElse(elem, false)) pull(in)
              else {
                history = history + (elem -> true)
                push(out, elem)
              }
            }
          })
          setHandler(out, new OutHandler {
            override def onPull(): Unit = {
              pull(in)
            }
          })
        }
      }
      )

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

    "example: statefulMapConcat" in {
      // given
      val in = List[Float](1,1, 2,2, 4,4)
      val flow: Flow[Float, Float, NotUsed] = Flow[Float].statefulMapConcat( () => {
        var queue = scala.collection.mutable.Queue[Float]()
        val size = 2

        (el) => {
          queue += el
          if (queue.size > size) queue.dequeue()
          List(queue.toList.sum / queue.size)
        }
      })

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

    "example: (not working) statefulMapConcat" in {
      // given
      val in1 = List[Char]('T', 'h', 'e', ' ', 't', 'h', 'r', 'e', 'e', ' ', 'w', 'o', 'r', 'd', 's', ' ', '!')
      val in2 = List[Char](' ', 'T', 'h', 'e', ' ', 't', 'h', 'r', 'e', 'e', ' ', 'w', 'o', 'r', 'd', 's', ' ', '!')
      val in3 = List[Char]('T', 'h', 'e', ' ', ' ', 't', 'h', 'r', 'e', 'e', ' ', 'w', 'o', 'r', 'd', 's', ' ', '!')
      val in4 = List[Char]('T', 'h', 'e', ' ', 't', 'h', 'r', 'e', 'e', ' ', 'w', 'o', 'r', 'd', 's', ' ', '!', ' ')

      val flow: Flow[Char, String, NotUsed] = Flow[Char].statefulMapConcat( () => {
        var last = ""
        (el) => {
          if (el != ' ' && el != 0.toChar) {
            last = last + el
            List()
          } else if (last.isEmpty) {
            List()
          } else {
            val send = last
            last = ""
            List(send)
          }
        }
      })

      // when
      def result(in: List[Char]): List[String] = Source.combine(Source(in), Source.single(0.toChar))(Concat(_))
        .via(flow)
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

    "example: GraphDSL" in {
      def parallelFlow[In, Out](workerCount: Int)(flow: Flow[In, Out, NotUsed]): Flow[In, Out, NotUsed] = {
        Flow.fromGraph(GraphDSL.create() { implicit builder =>
          import GraphDSL.Implicits._

          val balance = builder.add(Balance[In](workerCount, waitForAllDownstreams = true))
          val merge = builder.add(Merge[Out](workerCount))

          1 to workerCount foreach { _ =>
            balance ~> flow.async ~> merge
          }

          FlowShape(balance.in, merge.out)
        })
      }

      val flow = Flow[Int].map(longAction)

      Source(1 to 10)
        .via(parallelFlow(6)(flow))
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
