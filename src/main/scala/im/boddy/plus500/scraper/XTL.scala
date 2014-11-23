package im.boddy.plus500.scraper

import java.io._
import java.nio.file.Paths
import java.util.Date
import java.util.concurrent.{Callable, ExecutorService, Executors}

import scala.io.Source
import scala.util.control.Exception._


/**
 * Created by chris on 6/13/14.
 */
class XTL(val dbFile: File, val tickLength: Long = 300000, val nThread: Int = 8) extends Runnable {

  val threadPool : ExecutorService  = Executors.newFixedThreadPool(nThread)
  val loader = new Loader(dbFile)
  val symbols = XTL.getSymbols()
  @volatile var isClosed = false

  private def isUpdated(tick: Candlestick, previousTick: Option[Candlestick]) : Boolean = {
    if (previousTick.isEmpty)
      true
    else
      (! previousTick.get.copy(timestamp=0).equals(tick.copy(timestamp=0)))
  }

  def xtl(previousTicks : Map[String, Candlestick] = Map()) : Map[String, Candlestick] = {

    val futures = symbols.map(_.instrument).map (instrument => threadPool.submit(Task(instrument)))
    val newCandlesticks = futures.map(future => allCatch.opt(future.get())).flatten

    val updatedCandlesticks = newCandlesticks.filter(c => isUpdated(c, previousTicks.get(c.instrument)))

    try  {
      val updatedInstruments = loader.updateValues(updatedCandlesticks)
      println("updated "+ updatedInstruments.size  +" instruments  @ "+ new Date(System.currentTimeMillis()))

      //
      // updatedCandlesticks.foreach(candlestick => println(candlestick + " " + symbols.filter(_.instrument.equals(candlestick.instrument)).mkString))

    } catch {
      case e : Exception => e.printStackTrace()
    }

    val updatedTicks = updatedCandlesticks.map(c => (c.instrument -> c)).toMap[String, Candlestick]
    previousTicks ++ updatedTicks
  }

  def run {

    var previousTicks: Map[String, Candlestick] = Map()
    var counterTime = System.currentTimeMillis()
    while (! isClosed) {

      val now = System.currentTimeMillis()

      if (now < counterTime)
        try {
          Thread.sleep(counterTime - now)
        } catch { case t :Throwable => }

      counterTime += tickLength

      previousTicks = xtl(previousTicks)
    }


  }

  class Task(val instrument: String) extends Callable[Candlestick] {
    def call : Candlestick = {
      val page = Extractor.getInstrumentPage(instrument)
      val timestamp = System.currentTimeMillis()
      Transformer.extractCandleStick(page, instrument).copy(timestamp = timestamp)
    }
  }

  object Task {
    def apply(instrument: String) = {new Task(instrument)}
  }

  def close {
    isClosed = true
    loader.close
    threadPool.shutdown
  }

}

object XTL {

  def getSymbols(f: File = Paths.get("data", "symbols.txt").toFile) : List[Symbol] = Source.fromFile(f).getLines().map(getSymbol).flatten.toList

  private def getSymbol(line: String) : Option[Symbol] = {
    val s : Array[String] = line.split("=")
    if (s.startsWith("#") || s.startsWith("//") || s.length != 2)
      None
    else
      Some(Symbol(s(0).trim, s(1).trim))
  }

  def main(args: Array[String])
  {
    if (args.isEmpty)
      println(usage)
    else {
      val xtl = new XTL(new File(args.head))
      xtl.run()
    }
  }

  def usage = "usage: XTL dbFile"

  lazy val symbols : Map[String,String] = getSymbols().map(symbol => symbol.instrument -> symbol.description).toMap
  def getDescription(instrument : String): Option[String] = symbols.get(instrument)
}
