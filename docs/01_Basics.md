### Design

 - reactive-stream
   - `Sink.asPublisher`
   - `Source.asSubscriber`
 - building blocks
   - `Source (Unit -> out)`
   - `Sink (in -> Unit)`
   - `Flow (in -> out)`
   - `Fan-In ((in0, in1) -> out)`
   - `Fan-Out (in -> (out0, out1))`
   - `BidiFlow ( in0 -> out0) (in1 <- out1)`
   - `Graph (in* -> out*)`
   - `RunnableGraph (Unit -> Unit)`
 - blueprint + materialization 
    `(Materialization is the process of allocating all resources needed 
    to run the computation described by a Graph)`
 - almost all computation stages preserve input order. Unordered e.g.:
   - `Flow.mapAsyncUnordered`
   - multiple input streams (e.g. `Merge`)
 
### Simplification example

```scala
import akka.stream._
import akka.stream.scaladsl._
import akka.actor.ActorSystem

implicit val system = ActorSystem("QuickStart")
implicit val materializer = ActorMaterializer()

val flow = Flow[Int].map(x => x)  //          -> Flow[Int, Int, NotUsed]

Source(1 to 100)                  // in:      Int        -> Source
  .via(flow)                      // via:     Flow       -> Source
  .to(Sink.foreach(println))      // to:      Sink       -> RunnableGraph
  .run()(materializer)            // run:                -> R: MaterializedValue

Source(1 to 100)                  // in:      Int        -> Source
  .via(flow)                      // via:     Flow       -> Source
  .to(Sink.foreach(println))      // to:      Sink       -> RunnableGraph
  .run()                          // run:                -> R: MaterializedValue

Source(1 to 100)                  // in:      Int        -> Source
  .via(flow)                      // via:     Flow       -> Source
  .runWith(Sink.foreach(println)) // runWith: Sink       -> R: MaterializedValue

Source(1 to 100)                  // in:      Int        -> Source
  .via(flow)                      // via:     Flow       -> Source
  .runForeach(println)            // runWith: (-> Unit)  -> R: MaterializedValue

Source(1 to 100)                  // in:      Int        -> Source
  .map(x => x)                    // map:     (Int -> T) -> Source
  .runForeach(println)            // runWith: (-> Unit)  -> R: MaterializedValue
```

### Links

 - http://doc.akka.io/docs/akka/current/scala/general/stream/stream-design.html
 - http://doc.akka.io/docs/akka/current/scala/stream/stream-flows-and-basics.html
 - 
 - http://www.reactive-streams.org/
