import akka.{Done, NotUsed}
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.scalatest.Matchers

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class _07_Graphs extends WorkshopSpec with Matchers {

  import WorkshopSpec.Implicits._

  implicit val system = ActorSystem("06-Graphs")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  "GraphDSL" should {
    "create custom graph using GraphDSL" in {
      val runnableGraph: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val in = Source(1 to 10).map(viaLogger0)
        val out = Sink.ignore

        val bcast = builder.add(Broadcast[Int](2))
        val merge = builder.add(Merge[Int](2))

        val f1 = Flow[Int].map(_ * 10).map(viaLogger1)
        val f2 = Flow[Int].map(_ * 10).map(viaLogger2)
        val f3 = Flow[Int].map(_ * 10).map(viaLogger3)
        val f4 = Flow[Int].map(_ / 10).map(viaLogger4)

        in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
                    bcast ~> f4 ~> merge
        ClosedShape
      })

      runnableGraph.run()
      Thread.sleep(1000)
    }

    "create custom graph using GraphDSL with materialized values" in {
      val topHeadSink: Sink[Int, Future[Int]] = Sink.head[Int]
      val bottomHeadSink: Sink[Int, Future[Int]] = Sink.head[Int]
      val sharedDoubler = Flow[Int].map(_ * 2)

      val runnableGraph: RunnableGraph[(Future[Int], Future[Int])] =
        RunnableGraph.fromGraph(GraphDSL.create(topHeadSink, bottomHeadSink)((_, _)) { implicit builder => (topHS: SinkShape[Int], bottomHS: SinkShape[Int]) =>
          import GraphDSL.Implicits._
          val balance = builder.add(Balance[Int](2))

//          Source(List(1, 2)) ~> balance
//                                balance.out(0) ~> sharedDoubler.map(viaLogger1) ~> topHS.in
//                                balance.out(1) ~> sharedDoubler.map(viaLogger2) ~> bottomHS.in

//          Source(List(1, 2)) ~> balance
//                                balance ~> sharedDoubler.map(viaLogger1) ~> topHS
//                                balance ~> sharedDoubler.map(viaLogger2) ~> bottomHS

          Source(List(1, 2)) ~> balance ~> sharedDoubler.map(viaLogger1) ~> topHS
                                balance ~> sharedDoubler.map(viaLogger2) ~> bottomHS

          ClosedShape
      })

      val (res1, res2) = runnableGraph.run()
      res1.awaitOnCompleteAndLog()
      res2.awaitOnCompleteAndLog()
    }

    "create Source graph using GraphDSL" in {
      val source = Source.fromGraph(GraphDSL.create(){ implicit builder =>
        import GraphDSL.Implicits._

        val in = Source(1 to 10)

        val bcast = builder.add(Broadcast[Int](2))
        val merge = builder.add(Merge[Int](2))

        val f1 = Flow[Int].map(_ * 10).map(viaLogger1)
        val f2 = Flow[Int].map(_ * 10).map(viaLogger2)
        val f3 = Flow[Int].map(_ * 10).map(viaLogger3)
        val f4 = Flow[Int].map(_ / 10).map(viaLogger4)
        val f5 = Flow[Int].map(_ + 1).map(viaLogger5)

        val out = builder.add(f5)

        //        in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> f5
        in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
        bcast ~> f4 ~> merge

        SourceShape(out.out)
      })

      source
        .runForeach(toLogger0)
        .awaitOnCompleteAndLog()
    }

    "create Flow graph" in {
      val flow = Flow.fromGraph(GraphDSL.create(){ implicit builder =>
        import GraphDSL.Implicits._

        val bcast = builder.add(Broadcast[Int](3))
        val merge = builder.add(Merge[Int](3))

        val f1 = Flow[Int].map(_ * 1).map(viaLogger1)
        val f2 = Flow[Int].map(_ * 10).map(viaLogger2)
        val f3 = Flow[Int].map(_ * 100).map(viaLogger3)

        bcast ~> f1 ~> merge
        bcast ~> f2 ~> merge
        bcast ~> f3 ~> merge

        FlowShape(bcast.in, merge.out)
      })

//      flow
//       .runWith(Source(1 to 10), Sink.foreach(toLogger0))
//        ._2.awaitOnCompleteAndLog()

      Source(1 to 10)
        .via(flow)
        .runForeach(toLogger0)
        .awaitOnCompleteAndLog()
    }

    // Predefined Types Builder
    //
    // Source.fromGraph
    // Sink.fromGraph
    // Flow.fromGraph
    // RunnableGraph.fromGraph
    // BidiFlow.fromGraph

    // Predefined shapes
    //
    // ClosedShape, SourceShape, SinkShape, FlowShape
    // FanInShape1, FanInShape2, …
    // FanOutShape1, FanOutShape2, …
    // UniformFanInShape, UniformFanOutShape

    "connect 2 graphs" in {
//      val maxOfThree = GraphDSL.create() { implicit builder =>
//        val zip3 = builder.add(new ZipWith3[Int, Int, Int, Int](List(_, _, _).max))
//        new FanInShape3(zip3.in0, zip3.in1, zip3.in2, zip3.out)
//      }

      val maxOfThree: Graph[UniformFanInShape[Int, Int], NotUsed] = GraphDSL.create() { implicit builder =>
        val zip3 = builder.add(ZipWithN[Int, Int](_.max)(3))
        UniformFanInShape(zip3.out, zip3.in(0), zip3.in(1), zip3.in(2))
      }

      val graph = RunnableGraph.fromGraph(GraphDSL.create(Sink.head[Int]) { implicit builder => sink =>
        import GraphDSL.Implicits._

        val maxOfThreeShape = builder.add(maxOfThree)

        Source.single(0) ~> maxOfThreeShape
        Source.single(1) ~> maxOfThreeShape
        Source.single(2) ~> maxOfThreeShape  ~> sink

        ClosedShape
      })

      graph.run()
        .awaitOnCompleteAndLog()
    }

    "use materialized value" in {
      val foldSink = Sink.fold[Int, Int](0)(_ + _)

      val foldFlow: Flow[Int, Int, Future[Int]] = Flow.fromGraph(GraphDSL.create(foldSink) { implicit builder => fold =>
        import GraphDSL.Implicits._
        FlowShape(fold.in, builder.materializedValue.mapAsync(1)(_.map(_ * 100)).outlet)
      })

      val (r1, r2) = Source(1 to 10)
        .map(viaLogger0)
        .viaMat(foldFlow)(Keep.right)
        .toMat(Sink.foreach(toLogger2))(Keep.both)
        .run()

      r1.awaitOnCompleteAndLog()
      r2.awaitOnCompleteAndLog()
    }
  }

  "BidiFlow" should {
    val objectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    def toJson[T](t: T): String = objectMapper.writeValueAsString(t)
    def fromJson[T](clazz: Class[T])(json: String): T = objectMapper.readValue(json, clazz)

    def jsonBidi[T](clazz: Class[T]): BidiFlow[T, String, String, T, NotUsed] = BidiFlow.fromFunctions(toJson, fromJson(clazz))
    /**
      *         +----------------------+
      *         |  jsonBidi[T]         |
      *         |                      |
      *         |  +----------------+  |
      *      T ~~> |     toJson     | ~~> String
      *         |  +----------------+  |
      *         |  +----------------+  |
      * String <~~ |    fromJson    | <~~ T
      *         |  +----------------+  |
      *         +----------------------+
      */

    "as code example" in {
      // given
      val bidi = jsonBidi(classOf[Map[String, String]])
      val map = Map(
        "test" -> "123",
        "low"  -> "XYz",
      )

      // when
      val mapToUpper = bidi.join(Flow[String].map(_.toUpperCase))

      Source
        .single(map)
        .map(viaLogger1)
        .via(mapToUpper)
        .runForeach(toLogger0)
        .awaitOnCompleteAndLog()
    }
  }

  "Merge examples" should {
    "Source combine with Merge" in {
      // given
      val source1 = Source(List("A", "B", "C", "D"))
      val source2 = Source(List("1", "2", "3", "4"))
      val source3 = Source(List("v", "x", "y", "z"))

      // when
      Source
        .combine(source1, source2, source3)(Merge(_))
        .runForeach(toLogger0)
        .awaitOnCompleteAndLog()
    }

    "Source combine with Concat" in {
      // given
      val source1 = Source(List("A", "B", "C", "D"))
      val source2 = Source(List("1", "2", "3", "4"))
      val source3 = Source(List("v", "x", "y", "z"))

      // when
      Source
        .combine(source1, source2, source3)(Concat(_))
        .runForeach(toLogger0)
        .awaitOnCompleteAndLog()
    }

    "Sink combine with Broadcast" in {
      val sink1: Sink[Int, Future[Done]] = Sink.foreach[Int](toLogger1)
      val sink2: Sink[Int, Future[Done]] = Flow[Int].map(_ * 11).toMat(Sink.foreach(toLogger2))(Keep.right)

      Source(1 to 10)
        .runWith(Sink.combine(sink1, sink2)(Broadcast[Int](_)))

      Thread.sleep(1000)
    }

    "Sink combine with Balance" in {
      val sink1: Sink[Int, Future[Done]] = Sink.foreach[Int](toLogger1)
      val sink2: Sink[Int, Future[Done]] = Flow[Int].map(_ * 11).toMat(Sink.foreach(toLogger2))(Keep.right)

      Source(1 to 10)
        .runWith(Sink.combine(sink1, sink2)(Balance[Int](_)))

      Thread.sleep(1000)

    }

    "Sink alsoTo as Broadcast with materialized value" in {
      val sink1: Sink[Int, Future[Done]] = Sink.foreach[Int](toLogger1)
      val sink2: Sink[Int, Future[Done]] = Flow[Int].map(_ * 11).toMat(Sink.foreach(toLogger2))(Keep.right)

      val (sink1Res, sink2Res) = Source(1 to 10)
          .alsoToMat(sink1)(Keep.right)
          .toMat(sink2)(Keep.both)
          .run()

      sink1Res.awaitOnCompleteAndLog()
      sink2Res.awaitOnCompleteAndLog()
    }

    "Source zipN" in {
      // given
      val sources = scala.collection.immutable.Seq(
        Source(List("A", "B", "C", "D")),
        Source(List("1", "2", "3", "4")),
        Source(List("v", "x", "y", "z"))
      )

      Source
        .zipN(sources)
        .map(_.mkString("-"))
        .runForeach(toLogger0)
        .awaitOnCompleteAndLog()
    }

    "Source zipWithN" in {
      // given
      val sources = scala.collection.immutable.Seq(
        Source(List("A", "B", "C", "D")),
        Source(List("1", "2", "3", "4")),
        Source(List("v", "x", "y", "z"))
      )

      Source
        .zipWithN[String, String](_.mkString("-"))(sources)
        .runForeach(toLogger0)
        .awaitOnCompleteAndLog()
    }
  }

  "GraphDSL errors" should {
    "dead-lock #1" in {
      val graph = RunnableGraph.fromGraph(GraphDSL.create(Sink.foreach(toLogger0)) { implicit b => sink =>
        import GraphDSL.Implicits._

        val source       = Source(1 to 10)
        val flow         = Flow[Int].map(viaLogger1)
        val flow2        = Flow[Int].map(viaLogger2)
        //        val flow2        = Flow[Int].filter(_ % 3 == 0).map(viaLogger2)
        //        val flow2        = Flow[Int].mapConcat(i => (1 to 3).map(i*10 + _)).map(viaLogger2)
        val zip          = b.add(ZipN[Int](2))
        val bcast        = b.add(Broadcast[Int](2))

        source ~> bcast ~>  flow ~> zip ~> sink
                  bcast ~> flow2 ~> zip

        ClosedShape
      })

      graph.run().awaitOnCompleteAndLog(timeout = 1.second)
    }

    "dead-lock #2" in {
      val graph = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit b => sink =>
        import GraphDSL.Implicits._

        val source = Source(1 to 10)
        val flow   = Flow[Int].map(viaLogger0)
        val merge  = b.add(Merge[Int](2))
        val bcast  = b.add(Broadcast[Int](2))

        source ~> merge ~> flow ~> bcast ~> sink
                  merge     <~     bcast

        ClosedShape
      })

      graph.run().awaitOnCompleteAndLog(timeout = 1.second)
    }

    "missing events in loop #1" in {
      val graph = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit b => sink =>
        import GraphDSL.Implicits._

        val source       = Source(1 to 10)
        val flow         = Flow[Int].map(viaLogger0)
        val merge        = b.add(MergePreferred[Int](1))
        val bcast        = b.add(Broadcast[Int](2))

        source ~> merge             ~>    flow ~> bcast ~> sink
                  merge.preferred          <~     bcast

        ClosedShape
      })

      graph.run().awaitOnCompleteAndLog(timeout = 1.second)
    }

    "missing events in loop #2" in {
      val graph = RunnableGraph.fromGraph(GraphDSL.create(Sink.ignore) { implicit b => sink =>
        import GraphDSL.Implicits._

        val source       = Source(1 to 10)
        val flow         = Flow[Int].map(viaLogger0)
        val feedbackFlow = Flow[Int].buffer(4, OverflowStrategy.dropHead)
        val merge        = b.add(Merge[Int](2))
        val bcast        = b.add(Broadcast[Int](2))

        source ~> merge ~>     flow     ~> bcast ~> sink
                  merge <~ feedbackFlow <~ bcast

        ClosedShape
      })

      graph.run().awaitOnCompleteAndLog(timeout = 1.second)
    }
  }
}