package main.scala.im.boddy.plus500.scraper

import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl

import scala.xml.{Node, XML}

/**
 * Created by chris on 6/13/14.
 */
object Transformer {

  val allInstrumentsPage = "http://www.plus500.co.uk/AllInstruments/AllInstruments.aspx"
  val bidId = "ctl00_ContentPlaceMain1_LabelBuyPrice"
  val askId = "ctl00_ContentPlaceMain1_LabelSellPrice"
  val leverageId = "ctl00_ContentPlaceMain1_LabelLeverageHeader"
  val marginId = "ctl00_ContentPlaceMain1_LabelInitialMarginHeader"

  private val factory = new SAXFactoryImpl();

  def extractCandleStick(page: String, instrument: String) : Candlestick = {
    val xml =  XML.withSAXParser(factory.newSAXParser()).loadString(page)

    val spans = xml \\ "span"
    val trs = xml \\ "tr"

    // bid and ask prices
    val bid = spans.filter(hasAttribute(_, "id", bidId)).text.toDouble
    val ask = spans.filter(hasAttribute(_, "id", askId)).text.toDouble

    var leverage = ""
    var expiresDaily = ""
    var maintenanceMargin = 0.0f
    var initialMargin = 0.0f

    trs.foreach { tr =>
      //leverage
      if (! ((tr \\ "th"  \\ "span").filter(hasAttribute(_, "id", leverageId)).isEmpty)) {
        val tds = tr \ "td"

        leverage = tds.filter(_.text.contains(":")).text
        expiresDaily = tds.filter(isYesOrNo).text
      }

      //initial, maintenance margin
      else  if (! ((tr \\ "th"  \\ "span").filter(hasAttribute(_, "id", marginId)).isEmpty)) {
        val tds = tr \ "td"

        initialMargin  = tds.map( node => node.text.replace("%","").toFloat).max / 100
        maintenanceMargin  = tds.map( node => node.text.replace("%","").toFloat).min / 100
      }
    }

    Candlestick(instrument, bid, ask, leverage, initialMargin, maintenanceMargin)
  }

  val YesNo = Seq("Yes","No")
  def isYesOrNo(node: Node) : Boolean = {YesNo.contains(node.text)}

  def extractSymbols(page: String) : Seq[Symbol] = {
    val xml =  XML.withSAXParser(factory.newSAXParser()).loadString(page)

    val tableRows = xml \\ "tr"

    val symbols = for (row <- tableRows) yield {
      val tds = row \ "td"
      val symbol = tds.filter(hasAttribute(_, "class", "symbol"))
      val name = tds.filter(hasAttribute(_, "class", "name"))

      if (symbol.isEmpty || name.isEmpty)
        None
      else
        Some(Symbol(symbol.text.replace("\u00a0",""), name.text.trim))
    }
    symbols.flatten
  }

  private def hasAttribute(node: Node, name : String, value : String) : Boolean = {
    (node \ ("@"+name)).text.equals(value)
  }

}

case class Candlestick(instrument: String, bidPrice: Double, askPrice: Double, leverage: String, initialMargin: Double, maintenanceMargin: Double, timestamp: Long = 0) {

  implicit object OrderedCandlestick extends Ordering[Candlestick] {
    def compare(o1: Candlestick, o2: Candlestick) = (o1.timestamp - o2.timestamp).asInstanceOf[Int]
  }
  def priceAverage : Double = {(bidPrice + askPrice) / 2.}
}

case class Symbol(instrument: String, description: String)
