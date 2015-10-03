package bitcoin.ws

import akka.stream.scaladsl.Source
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import support.AkkaStreamsTest
import scala.concurrent.Await
import scala.concurrent.duration._

class TickPublisherSpec extends WordSpec with Matchers with ScalaFutures with AkkaStreamsTest{

  "TickPublisher" should {

    "emit elements" in {
      val mean = 50
      val count = 100
      val durations = PoissonDelayIterator(mean).take(count).map(_.toLong.millis)
      val tickSource = Source.actorPublisher[FiniteDuration](TickPublisher.props(durations))

      val future = tickSource.runFold(Seq.empty[FiniteDuration])(_ :+ _)

      val ticks = Await.result(future, mean.millis * count * 2)

      val sum = ticks.map(_.toMillis).sum
      val average = sum / count

      log.info(s"average duration: $average")

      ticks should have size count
      average should equal (mean.toLong +- 15)

    }

  }

}
