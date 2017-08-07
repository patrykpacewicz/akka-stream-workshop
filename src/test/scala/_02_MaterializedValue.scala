import akka.Done
import akka.actor._
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.{Future, Promise}

class _02_MaterializedValue extends WorkshopSpec {

  import WorkshopSpec.Implicits._

  implicit val system = ActorSystem("02-MaterializedValue")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  "Sink" should {
    "fold" in {
      // when
      val result: Future[Int] = Source(1 to 10).map(viaLogger1)
        .runFold(0)(_ + _)
      //        .toMat(Sink.fold(0)(_ + _))(Keep.right).run()

      // wait
      result.awaitOnCompleteAndLog()
    }

    //   reduce( (T, T) => T): Sink[T, Future[T]]
    //   foldAsync(zero: U)((U, T) ⇒ Future[U]): Sink[T, Future[U]]
    //   lazyInit (T ⇒ Future[Sink[T, M]])

    "queue" in {
      // when
      val queue: SinkQueueWithCancel[Int] = Source(1 to 3).map(viaLogger1)
        .runWith(Sink.queue())

      // then
      val res1 = queue.pull()
      val res2 = queue.pull()
      val res3 = queue.pull()
      val res4 = queue.pull()

      res1.logOnComplete()
      res2.logOnComplete()
      res3.logOnComplete()
      res4.logOnComplete()

      // wait
      Future.sequence(Seq(res1, res2, res3, res4))
        .awaitOnCompleteAndLog()
    }

    "actorRef" in {
      case object Complete

      val actorRef = system.actorOf(Props(new Actor {
        override def receive: Receive = {
          case x => logger.info(s"in actor: $x")
        }
      }))

      Source(List("A", "B", "C"))
        .map(viaLogger1)
        .runWith(Sink.actorRef(actorRef, Complete))

      Thread.sleep(1000)
    }

    "actorRefWithAck" in {
      case object OnInit
      case object Ack
      case object Complete

      val actorRef = system.actorOf(Props(new Actor {
        override def receive: Receive = {
          case x =>
            logger.info(s"in actor: $x")
            logger.info(s"sender() ! Ack")
            sender() ! Ack
        }
      }))

      Source(List("A", "B", "C"))
        .map(viaLogger1)
        .runWith(Sink.actorRefWithAck(actorRef, OnInit, Ack, Complete))

      Thread.sleep(1000)
    }
  }

  "Source" should {
    "as maybe" in {
      // when
      val source = Source.maybe[String]
      //      val result: Future[Done] = source.runForeach(toLogger)
      //      val result: Promise[Option[String]] = source.to(Sink.foreach(toLogger)).run()
      val (promise: Promise[Option[String]], result: Future[Done]) = source
        .toMat(Sink.foreach(toLogger1))(Keep.both).run()
      result.logOnComplete()

      // when
      promise.success(Some("X"))
      //      promise.success(None)
      //      promise.failure(new Exception("maybe - fail"))

      // wait
      result.await()
    }

    "as queue" in {
      // when
      val source = Source.queue[String](Int.MaxValue, OverflowStrategy.backpressure)
      val (queue: SourceQueueWithComplete[String], result: Future[Done]) = source
        .toMat(Sink.foreach(toLogger1))(Keep.both)
        .run()
      result.logOnComplete()

      queue.offer("A")
      queue.offer("B")
      queue.offer("C")
      queue.offer("D")
      queue.offer("E")

      queue.complete()
      //      queue.fail(new Exception("queue fail"))

      queue.offer("X")
      queue.offer("Y")
      queue.offer("Z")

      // wait
      result.await()
    }

    "as actorRef" in {
      // given
      case class Expl(in: String)

      // when
      val source = Source.actorRef[Expl](Int.MaxValue, OverflowStrategy.fail)
      val (actor: ActorRef, result: Future[Done]) = source
        .toMat(Sink.foreach(toLogger1))(Keep.both)
        .run()
      result.logOnComplete()

      // when
      actor ! Expl("A")
      actor ! Expl("B")
      actor ! Expl("C")
      actor ! Expl("D")
      actor ! Expl("E")
      //      actor ! akka.actor.Status.Success(Done)
      //      actor ! akka.actor.Status.Failure(new Exception("Failure"))
      //      actor ! akka.actor.PoisonPill
      actor ! Expl("X")
      actor ! Expl("Y")
      actor ! Expl("Z")

      // wait
      result.await()
    }

    "as subscriber to Publisher" in {
      // given
      val fivelist: List[String] = List("A", "B", "C", "D", "E")

      // when
      val (sub: Subscriber[String], pub: Publisher[String]) =
        Source.asSubscriber[String].toMat(Sink.asPublisher(false))(Keep.both).run()

      Source(fivelist).to(Sink.fromSubscriber(sub)).run()

      val result =
        Source.fromPublisher(pub).runForeach(toLogger1)

      // wait
      result.await()
    }

    //    "lazily" in { ... }    // in: () => Source
  }
}
