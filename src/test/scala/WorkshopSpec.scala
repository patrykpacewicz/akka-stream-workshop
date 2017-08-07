import com.typesafe.scalalogging.Logger
import org.scalatest.WordSpec
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.concurrent.duration._

trait WorkshopSpec
  extends WordSpec {
  protected implicit val logger: Logger = Logger(LoggerFactory.getLogger(getClass.getName))

  protected def viaLogger0[T](t: T): T = useLogger("via0")(t)
  protected def viaLogger1[T](t: T): T = useLogger("via1")(t)
  protected def viaLogger2[T](t: T): T = useLogger("via2")(t)
  protected def viaLogger3[T](t: T): T = useLogger("via3")(t)
  protected def viaLogger4[T](t: T): T = useLogger("via4")(t)
  protected def viaLogger5[T](t: T): T = useLogger("via5")(t)
  protected def viaLogger6[T](t: T): T = useLogger("via6")(t)
  protected def viaLogger7[T](t: T): T = useLogger("via7")(t)
  protected def viaLogger8[T](t: T): T = useLogger("via8")(t)
  protected def viaLogger9[T](t: T): T = useLogger("via9")(t)

  protected def toLogger0[T](t: T): Unit = useLogger("to0")(t)
  protected def toLogger1[T](t: T): Unit = useLogger("to1")(t)
  protected def toLogger2[T](t: T): Unit = useLogger("to2")(t)
  protected def toLogger3[T](t: T): Unit = useLogger("to3")(t)

  protected def useLogger[T](prefix: String)(t: T): T = {
    logger.info(s"[$prefix] ${t.toString}")
    t
  }
}


object WorkshopSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  object Implicits {

    implicit class FutureOps[T](val future: Future[T]) extends AnyVal {
      def awaitOnCompleteAndLog(name: String = "", timeout: Duration = 10.seconds)(implicit logger: Logger): T = {
        logOnComplete(name)
        await(timeout)
      }
      def await(timeout: Duration = 10.seconds): T = {
        Await.result(future, timeout)
      }
      def logOnComplete(name: String = "")(implicit logger: Logger): Unit = {
        future.onComplete(result => logger.info(s"[$name] Stream completed: $result"))
      }

    }

  }

}