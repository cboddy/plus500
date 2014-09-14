package main.scala.im.boddy.plus500.scraper

import java.io.{BufferedReader, InputStreamReader}
import java.net._

/**
 * Created by chris on 6/11/14.
 */
object Extractor
{
  def getInstrumentUrl(instrument: String) = "http://www.plus500.co.uk/Instruments/"+ instrument

  val userAgent  ="Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2"

  def getUrl(url: String, asUserAgent : Option[String] = Some(userAgent)) = {
    val conn = new URL(url).openConnection();
    if (asUserAgent.isDefined)
      conn.setRequestProperty("User-Agent", asUserAgent.get);
    conn.connect();

    val reader = new BufferedReader(new InputStreamReader(conn.getInputStream));
    var s : String = null
    val sb = new StringBuilder()
    while ({s = reader.readLine; s} != null)
       sb.append(s)
    reader.close()
    sb.toString()
  }

  def getInstrumentPage(instrument: String) = getUrl(getInstrumentUrl(instrument))
}

