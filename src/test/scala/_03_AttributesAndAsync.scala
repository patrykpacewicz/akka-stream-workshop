import akka.actor._
import akka.event.Logging
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorAttributes, ActorMaterializer, Attributes, OverflowStrategy}
import scala.concurrent.duration._

class _03_AttributesAndAsync extends WorkshopSpec {
  import WorkshopSpec.Implicits._

  implicit val system = ActorSystem("03-Attributes")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  "Attributes" should {
    "setLevels" in {
      val debugLevel = Attributes.logLevels(Logging.DebugLevel, Logging.DebugLevel)
      val infoLevel = Attributes.logLevels(Logging.InfoLevel, Logging.InfoLevel)
      val warnLevel = Attributes.logLevels(Logging.WarningLevel, Logging.WarningLevel)

      val flowDebug = Flow[Int].map(_ * 10).log("log-debug").withAttributes(debugLevel)
      val flowInfo = Flow[Int].map(_ * 10).log("log-info").withAttributes(infoLevel)
      val flowNone = Flow[Int].map(_ * 10).log("log-NONE")

      Source(1 to 10)
        .via(flowNone)
        .via(flowDebug)
        .via(flowInfo)
        .to(Sink.foreach(toLogger1))
        .withAttributes(warnLevel)
        .run()

      Thread.sleep(1000)
    }

    "async" in {
      val infoLevel = Attributes.logLevels(Logging.InfoLevel, Logging.InfoLevel)

      val flow1 = Flow[Int].map(x => { Thread.sleep(1000); x * 11 })
      val flow2 = Flow[Int].map(x => { Thread.sleep(1000); x * 10 })

      Source(List(1, 2, 3, 4))
        .via(flow1.log("async").async)
        .via(flow2.log("none-async"))
        .to(Sink.ignore)
        .addAttributes(infoLevel)
        .run()

      Thread.sleep(10 * 1000)
    }

    "dispacher" in {
      val infoLevel = Attributes.logLevels(Logging.InfoLevel, Logging.InfoLevel)
      val blockingDisp = ActorAttributes.dispatcher("akka.actor.default-dispatcher")

      val flow1 = Flow[Int].map(x => { Thread.sleep(1000); x * 11 })
      val flow2 = Flow[Int].map(x => { Thread.sleep(1000); x * 10 })

      Source(List(1, 2, 3, 4))
        .via(flow1.log("async").addAttributes(blockingDisp))
        .via(flow2.log("none-async"))
        .to(Sink.ignore)
        .addAttributes(infoLevel)
        .run()

      Thread.sleep(10 * 1000)
    }

    "blocking queue example" in {
      def longFlow[T] = Flow[T].map(x => {Thread.sleep(2 * 1000) ; x})

      val queue = Source
        .queue[String](Int.MaxValue, OverflowStrategy.backpressure).async
        .via(longFlow)
        .to(Sink.foreach(toLogger1))
        .run()

      1 to 10 foreach { i =>
        logger.info(s"run: queue.offer($i)")
        queue.offer(i.toString).awaitOnCompleteAndLog(timeout = 1.second)
      }
    }
  }

  //  Attributes      -> name
  //  Attributes      -> inputBuffer
  //  ActorAttributes -> supervisionStrategy
}
