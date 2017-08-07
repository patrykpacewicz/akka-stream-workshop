import akka.{Done, NotUsed}
import akka.actor._
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.stage.{InHandler, OutHandler, _}
import org.scalatest.Matchers

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class _08_CustomizeGraphStage extends WorkshopSpec with Matchers {

  import WorkshopSpec.Implicits._

  implicit val system = ActorSystem("08-CustomizeGraphStage")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  "GraphStage" should {
    "simple Source" in {
//      class SimpleSource extends GraphStage[SourceShape[Int]] {
//        override def shape: SourceShape[Int] = ???
//        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = ???
//      }
      class SimpleSource extends GraphStage[SourceShape[Int]] {
        val out: Outlet[Int] = Outlet("SimpleSource")
        override val shape: SourceShape[Int] = SourceShape(out)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
          private var mutableState = 1

          setHandler(out, new OutHandler {
            override def onPull(): Unit = {
              push(out, mutableState)
              mutableState += 1
            }
          })
        }
      }

      val source: Source[Int, NotUsed] = Source.fromGraph(new SimpleSource())

      source.take(10).runForeach(toLogger0)
      source.take(3).runForeach(toLogger1)

      Thread.sleep(1000)
    }

    "simple Sink" in {
      class SimpleSink extends GraphStage[SinkShape[Int]] {
        val in: Inlet[Int] = Inlet("SimpleSink")
        override def shape: SinkShape[Int] = SinkShape(in)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

          override def preStart(): Unit = pull(in)

          setHandler(in, new InHandler {
            override def onPush(): Unit = {
              val data = grab(in)
              logger.info(s"data: $data")
              pull(in)
            }
          })
        }
      }

      Source(1 to 10)
          .to(new SimpleSink())
          .run()

      Thread.sleep(1000)
    }

    "example Flow -> Map" in {
//      class Map[A, B](f: A => B) extends GraphStage[FlowShape[A, B]] {
//        val in = Inlet[A]("Map.in")
//        val out = Outlet[B]("Map.out")
//        override val shape = FlowShape.of(in, out)
//
//        override def createLogic(attr: Attributes): GraphStageLogic =
//          new GraphStageLogic(shape) {
//            setHandler(in, new InHandler {
//              override def onPush(): Unit = {
//                push(out, f(grab(in)))
//              }
//            })
//            setHandler(out, new OutHandler {
//              override def onPull(): Unit = {
//                pull(in)
//              }
//            })
//          }
//      }

      class Map[A, B](f: A => B) extends GraphStage[FlowShape[A, B]] {
        val in = Inlet[A]("Map.in")
        val out = Outlet[B]("Map.out")
        override val shape = FlowShape.of(in, out)

        override def createLogic(attr: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler with OutHandler {

            override def onPush(): Unit = push(out, f(grab(in)))

            override def onPull(): Unit = pull(in)

            setHandlers(in, out, this)
          }
      }

      Source(1 to 10)
        .via(new Map(_ * 10))
        .runForeach(toLogger0)

      Thread.sleep(1000)
    }

    "example Flow -> Filter" in {
      class Filter[A](p: A => Boolean) extends GraphStage[FlowShape[A, A]] {
        val in = Inlet[A]("Filter.in")
        val out = Outlet[A]("Filter.out")
        override val shape = FlowShape.of(in, out)

        override def createLogic(attr: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) with InHandler with OutHandler {

            override def onPush(): Unit = {
              val elem = grab(in)
              if (p(elem)) push(out, elem)
              else pull(in)
            }

            override def onPull(): Unit = pull(in)

            setHandlers(in, out, this)
          }
      }

      Source(1 to 10)
        .via(new Filter(_ % 3 == 0))
        .runForeach(toLogger0)

      Thread.sleep(1000)
    }

    "onTimer" in {
      class OnTimerIgnore[A](ignorePeriod: FiniteDuration) extends GraphStage[FlowShape[A, A]] {
        val in = Inlet[A]("OnTimerIgnore.in")
        val out = Outlet[A]("OnTimerIgnore.out")
        override val shape = FlowShape.of(in, out)

        override def createLogic(attr: Attributes): GraphStageLogic =
          new TimerGraphStageLogic(shape) with InHandler with OutHandler {

            var isOpen = true

            override def onPush(): Unit = {
              val elem = grab(in)
              if (!isOpen) pull(in)
              else {
                push(out, elem)
                isOpen = false
                scheduleOnce(None, ignorePeriod)
              }
            }

            override def onPull(): Unit = pull(in)

            override protected def onTimer(timerKey: Any): Unit = isOpen = true

            setHandlers(in, out, this)
          }
      }

      Source(1 to 10)
        .throttle(1, 100.millis, 0, ThrottleMode.Shaping)
        .via(new OnTimerIgnore(300.millis))
        .runForeach(toLogger0)

      Thread.sleep(1000)
    }

    "SinglePromiseValue" in {
      class SinglePromiseSource[A] extends GraphStageWithMaterializedValue[SourceShape[A], Promise[A]] {
        val out = Outlet[A]("SinglePromiseSource.out")
        val shape = SourceShape.of(out)

        override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Promise[A]) = {
          val promise = Promise[A]()
          val logic = new GraphStageLogic(shape) with OutHandler {

            override def onPull(): Unit = {
              val x = Await.result(promise.future, Duration.Inf)
              push(out, x)
              completeStage()
            }

            setHandler(out, this)
          }
          (logic, promise)
        }
      }

      val (promise: Promise[Int], future: Future[Done]) = Source.fromGraph(new SinglePromiseSource[Int]())
          .toMat(Sink.foreach(toLogger0))(Keep.both)
          .run()

      promise.success(1234)
      future.awaitOnCompleteAndLog()
    }
  }
}