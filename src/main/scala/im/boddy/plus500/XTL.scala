package im.boddy.plus500

import java.io._
import java.nio.file.Paths
import java.util.concurrent.{ExecutorService, Executors, Callable}
import java.util.Date
import scala.io.Source
import scala.util.control.Exception._


/**
 * Created by chris on 6/13/14.
 */
class XTL(val dbFile: File, val tickLength: Long = 60000, val nThread: Int = 8) extends Runnable {

  val threadPool : ExecutorService  = Executors.newFixedThreadPool(nThread)
  val loader = new Loader(dbFile)
  val symbols = XTL.getSymbols()
  @volatile var isClosed = false

  private def isUpdated(tick: CandleStick, previousTick: Option[CandleStick]) : Boolean = {
    if (previousTick.isEmpty)
      true
    else
      (! previousTick.get.copy(timestamp=0).equals(tick.copy(timestamp=0)))
  }

  private [plus500] def xtl(previousTicks : Map[String, CandleStick] = Map()) : Map[String, CandleStick] = {

    val futures = symbols.map(_.instrument).map (instrument => threadPool.submit(Task(instrument)))
    val newCandlesticks = futures.map(future => allCatch.opt(future.get())).flatten

    val updatedCandlesticks = newCandlesticks.filter(c => isUpdated(c, previousTicks.get(c.instrument)))

    try  {
      val updatedInstruments = loader.updateValues(updatedCandlesticks)
      println("updated "+ updatedInstruments.size  +" instruments  @ "+ new Date(System.currentTimeMillis()))
    } catch {
      case e : Exception => e.printStackTrace()
    }

    val updatedTicks = updatedCandlesticks.map(c => (c.instrument -> c)).toMap[String, CandleStick]
    previousTicks ++ updatedTicks
  }

  def run {

    var previousTicks: Map[String, CandleStick] = Map()
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

  class Task(val instrument: String) extends Callable[CandleStick] {
    def call : CandleStick = {
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
}
