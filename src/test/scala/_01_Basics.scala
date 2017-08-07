import akka.actor.{ActorSystem, Cancellable}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}

import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util._

class _01_Basics extends WorkshopSpec {

  import WorkshopSpec.Implicits._

  implicit val system = ActorSystem("01-Basics")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  "Source simple" should {
    "apply" in {
      // given
      val fivelist: List[String] = List("A", "B", "C", "D", "E")

      // when
      // val source: Source[String, NotUsed] = Source(fivelist)
      // val result: Future[Done] = source.runForeach(x => logger.info(x))
      // result.onComplete(r => logger.info(s"Stream completed: $r"))
      // Await.ready(result, 10.seconds)

      // when
      val source: Source[String, NotUsed] = Source(fivelist)
      val result: Future[Done] = source.runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "single" in {
      // when
      val source: Source[String, NotUsed] = Source.single("A")
      val result: Future[Done] = source.runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "fromIterator" in {
      // given
      val fivelist: List[String] = List("A", "B", "C", "D", "E")

      // when
      val source = Source.fromIterator(() => fivelist.iterator)
      val result: Future[Done] = source.runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "repeat" in {
      // when
      val source = Source.repeat("A").take(12)
      val result: Future[Done] = source.runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "cycle" in {
      // given
      val fivelist: List[String] = List("A", "B", "C", "D", "E")

      // when
      val source = Source.cycle(() => fivelist.iterator).take(12)
      val result: Future[Done] = source.runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "tick" in {
      // given
      val fivelist: List[String] = List("A", "B", "C", "D", "E")

      // when
      val source: Source[String, Cancellable] = Source
        .tick(100.milliseconds, 200.milliseconds, fivelist.iterator)
        .take(4).map(_.next())
      val result: Future[Done] = source.runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "unfold - naive example" in {
      // when
      val source = Source.unfold((10, 0))({ case (unAcc: Int, iter: Int) =>
        if (unAcc == 0) None
        else Some((unAcc - iter) -> (iter + 1) -> iter)
      })
      val result: Future[Done] = source.runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    //    unfoldAsync()           // in: Future[Option[S, E]]
    //    unfoldResource()        // create: () ⇒ S,         read: (S) ⇒ Option[T],         close: (S) ⇒ Unit
    //    unfoldResourceAsync()   // create: () ⇒ Future[S], read: (S) ⇒ Future[Option[T]], close: (S) ⇒ Future[Done]

    "fromFuture" in {
      // when
      val source = Source.fromFuture(Future.successful("A"))
      val result: Future[Done] = source.runForeach(toLogger1)

      // wait
      result.awaitOnCompleteAndLog()
    }

    //    "fromCompletionStage" in { ... }          // in: CompletionStage
    //    "fromFutureSource" in { ... }             // in: Future[Source]
    //    "fromGraph" in { ... }                    // in: Graph[SourceShape]
    //    "fromSourceCompletionStage()" in { ... }  // in: CompletionStage[Graph[SourceShape]]
  }

  "Sink simple" should {
    "ignore" in {
      // when
      val result: Future[Done] = Source(List("A", "B", "C"))
        .map(viaLogger1)
        .runWith(Sink.ignore)

      // wait
      result.awaitOnCompleteAndLog()
    }

    "cancelled" in {
      // when
      Source(List("A", "B", "C"))
        .map(viaLogger1)
        .runWith(Sink.cancelled)

      // wait
      Thread.sleep(1000)
    }

    "onComplete" in {
      // when
      val result: NotUsed = Source(List("A", "B", "C"))
        .map(viaLogger1)
        .runWith(Sink.onComplete {
          case res: Success[_] => logger.info(res.toString)
          case res: Failure[_] => logger.warn(res.toString)
        })

      // wait
      Thread.sleep(1000)
    }

    // Sink.head               //                     => Future[T]
    // Sink.headOption         //                     => Future[Option[T]]
    // Sink.last               //                     => Future[T]
    // Sink.lastOption         //                     => Future[Option[T]]
    // Sink.seq                //                     => Future[Seq[T]]
    // Sink.foreach()          //  (T ⇒ Unit)         => Future[Done]
    // Sink.foreachParallel()  //  (Int)(f: T ⇒ Unit) => Future[Done]
  }

  "Flow simple" should {
    // map in { ... }
    // filter in { ... }
    // fold, foldAsync, reduce (similar to Sink)
    // scan, scanAsync (sort of online fold, see below)
    // drop, take, dropWhile, takeWhile

    // map + filter
    "collect" in {
      Source(1 to 10).collect {
        case x if x % 2 == 0 => x * 10
        case x if x % 5 == 0 => x * 100
      }.runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "mapConcat" in {
      Source(List("AA", "bb", "Cc"))
        .mapConcat(_.toUpperCase())
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "mapConcat 2" in {
      // given
      Source(List("AA", "bb", "Cc"))
        .mapConcat(_.toUpperCase() :: Nil)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "grouped" in {
      Source(1 to 10)
        .grouped(4)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "sliding" in {
      Source(1 to 10)
        .sliding(3, step = 2)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "limit" in {
      Source(1 to 10)
        .limit(4)
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "intersperse" in {
      Source(1 to 3)
        .intersperse("[", ",", "]")
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }

    "statefulMapConcat" in {
      Source(1 to 10)
        .statefulMapConcat(() => {
          val maxSize = 4
          val queue = scala.collection.mutable.Queue.empty[Int]

          (i) => {
            if (queue.size >= maxSize) queue.dequeue
            queue += i
            List(queue.toList)
          }
        })
        .runForeach(toLogger1)
        .awaitOnCompleteAndLog()
    }
  }
}
