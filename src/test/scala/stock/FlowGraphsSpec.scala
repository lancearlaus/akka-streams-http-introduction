package stock

import akka.stream.scaladsl._
import akka.util.{ByteString, ByteStringBuilder}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import streams.AkkaStreamsTest
import FlowGraphs._

import scala.concurrent.Future

class FlowGraphsSpec extends FlatSpec with AkkaStreamsTest with Matchers with ScalaFutures {

  import stock.FlowGraphs._

  val inCsv =
    """
      |Date,       Open,  High,  Low,   Close, Volume,  Adj Close
      |2014-12-31, 25.3,  25.3,  24.19, 24.84, 1438600, 24.84
      |2014-12-30, 26.28, 26.37, 25.29, 25.36, 766100,  25.36
      |2014-12-29, 26.64, 26.8,  26.13, 26.42, 619700,  26.42
      |2014-12-26, 27.25, 27.25, 26.42, 26.71, 360400,  26.71
    """.stripMargin.trim

  val expCsv =
    """
      |Date,       Open,  High,  Low,   Close, Volume,  Adj Close, Adj Close SMA(3)
      |2014-12-31, 25.3,  25.3,  24.19, 24.84, 1438600, 24.84,     25.54
      |2014-12-30, 26.28, 26.37, 25.29, 25.36, 766100,  25.36,     26.16
    """.stripMargin.trim


  "SMA flow" should "calculate properly" in {

    val future = Source(1 to 5)
      .map(n => (n*n).toDouble)
      .via(calculate.sma(3))
      .runFold(List.empty[Double])(_ :+ _)
//      .runForeach(sma => println(f"$sma%1.2f")

    whenReady(future)(_.map(_.formatted("%1.2f")) shouldBe List("4.67", "9.67", "16.67"))

  }


  "flow" should "append SMA" in {
//    val inSource = SynchronousFileSource(new File("input.csv"))
//    val expSource = SynchronousFileSource(new File("expected.csv"))
//    val outSink = SynchronousFileSink(new File("output.csv"))
//    val outSource = SynchronousFileSource(new File("output.csv"))

    val inSource = Source.single(ByteString(inCsv))
    val expSource = Source.single(ByteString(expCsv))
    val builder = new ByteStringBuilder()
    val outSink = Sink.foreach[ByteString](bs => builder ++= bs)
    val outSource = Source(() => Iterator.single(builder.result()))

//    println(s"inputCsv: $inputCsv")

    val window = 3
    val smaName = s"Adj Close SMA($window)"

    val future = inSource.via(csv.parse().via(quote.appendSma(window)).via(csv.format)).runWith(outSink)

    whenReady(future) { unit =>

//      println(s"output: ${builder.result.utf8String}")

      // Inspect row counts
      val countRows = csv.parse().fold(0)((c, _) => c + 1).toMat(Sink.head)(Keep.right)
      val inCount = inSource.runWith(countRows)
      val outCount = outSource.runWith(countRows)

      whenReady(Future.sequence(Seq(inCount, outCount))) {
        case inLines :: outLines ::  Nil =>
          outLines shouldBe (inLines - window + 1)
      }

      // Inspect header fields
      val inFieldsFuture = inSource.via(csv.parse()).runWith(Sink.head)
      val outFieldsFuture = outSource.via(csv.parse()).runWith(Sink.head)

      whenReady(Future.sequence(Seq(inFieldsFuture, outFieldsFuture))) {
        case inFields :: outFields :: Nil =>
          outFields shouldBe (inFields :+ smaName)
      }

      // Compare SMA column from output and expected
      val selectSma = csv.parse().via(csv.select(smaName)).drop(1).map(_.toDouble)
      val outFuture = outSource.via(selectSma).runFold(List.empty[Double])(_ :+ _)
      val expFuture = expSource.via(selectSma).runFold(List.empty[Double])(_ :+ _)

      whenReady(Future.sequence(Seq(outFuture, expFuture))) {
        case out :: exp :: Nil =>
          out should have size exp.size
          out.zip(exp).foreach { case (out, exp) =>
            out shouldBe exp
          }
      }

    }
  }

}
