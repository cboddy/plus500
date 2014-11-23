package im.boddy.plus500.scraper

import java.io.File
import java.sql._
import javax.sql.rowset.CachedRowSet

import com.sun.rowset.CachedRowSetImpl

import scala.collection.mutable.ListBuffer
;

/**
 * Created by chris on 6/12/14.
 */
class Loader (private val dbFile: File) {

  try
  {
    Class.forName("org.sqlite.JDBC")
  } catch {
    case cnfe : ClassNotFoundException => throw new SQLException(cnfe);
  }

  val url = "jdbc:sqlite:"+dbFile.getAbsolutePath()
  private val connection  = DriverManager.getConnection(url)
  connection.setAutoCommit(false)

  private val symbols : List[Symbol] = XTL.getSymbols()
  private val instruments : Set[String] = symbols.map(_.instrument).toSet
  private val rows = init
  private val idMap : Map[String, Int] = rows.map(row => row.symbol.instrument -> row.id).toMap

  @volatile var isClosed = false

  private def init : List[MetaDataRow] =  {
    val rs  = executeQuery(Loader.TABLE_NAMES_SELECT_STMT)

    val presentTables  = new collection.mutable.ListBuffer[String]()
    while (rs.next())
      presentTables += rs.getString("name")

    if (! presentTables.contains("instruments"))
      executeUpdate(Loader.CREATE_METADATA_TABLE_STMT)
    if (! presentTables.contains("ticks"))
      executeUpdate(Loader.CREATE_TICKS_TABLE_STMT)

    val presentMetadataRows = new collection.mutable.ListBuffer[MetaDataRow]()

    val rss = executeQuery("select * from instruments")
    while (rss.next())
    {
      val id = rss.getInt("id")
      val instrument = rss.getString("instrument")
      val description = rss.getString("description")
      presentMetadataRows += MetaDataRow(id, Symbol(instrument, description))
    }

    val presentSymbols: List[Symbol] = presentMetadataRows.map(row => row.symbol).toList
    val presentInstruments = presentSymbols.map(_.instrument)

    for (symbol <-  symbols)
      if (! presentInstruments.contains(symbol.instrument))
      {
        println("updating symbol "+ symbol +" in instruments")
        val insert = "insert into instruments (instrument, description) VALUES ('"+ symbol.instrument+"', '"+ symbol.description+"');"
        executeUpdate(insert)
      }

    presentMetadataRows.clear()
    val rsss = executeQuery("select * from instruments")
    while (rsss.next())
    {
      val id = rsss.getInt("id");
      val instrument = rsss.getString("instrument")
      val description = rsss.getString("description")
      presentMetadataRows += MetaDataRow(id, Symbol(instrument, description))
    }
    presentMetadataRows.toList
  }

  private def executeUpdate(update : String, commit :Boolean = true) {
    if (isClosed)
      throw new IllegalStateException(this + " is closed")

    val stmt : Statement = connection.createStatement()
    stmt.executeUpdate(update)
    stmt.close()
    if (commit)
      connection.commit()
  }

  def updateValues(candlesticks : List[Candlestick]) = {

    if (isClosed)
      throw new IllegalStateException(this + " is closed")

    val stmt = connection.prepareStatement("insert into ticks (timestamp, instrumentId, bidPrice, askPrice, leverage, initialMargin, maintenanceMargin, overnightPremium) VALUES(?, ?, ?, ?, ?, ?, ?, ?);")

    def addToBatch(candlestick : Candlestick) : Option[String] = {
      if (! instruments.contains(candlestick.instrument)) {
        println("instrument " + candlestick.instrument + " does not exist in instruments " + instruments)
        None
      }
      else if (getId(candlestick.instrument) < 0)
      {
        println("Cannot find id for instrument "+ candlestick.instrument +" in "+ idMap)
        None
      }
      else {
        //println("adding to batch "+ candlestick.instrument)
        stmt.setLong(1, candlestick.timestamp)
        stmt.setInt(2, getId(candlestick.instrument))
        stmt.setDouble(3, candlestick.bidPrice)
        stmt.setDouble(4, candlestick.askPrice)
        stmt.setString(5, candlestick.leverage)
        stmt.setDouble(6, candlestick.initialMargin)
        stmt.setDouble(7, candlestick.maintenanceMargin)
        stmt.setDouble(8, candlestick.overnightPremium)
        stmt.addBatch()
        Some(candlestick.instrument)
      }
    }

    val updatedInstruments : List[String] = candlesticks.flatMap(addToBatch)
    val batch = stmt.executeBatch()
    val nFailed = batch.filter(_ != 1).size
    if (nFailed >0)
      println(nFailed +" updates failed.")

    connection.commit()

    //println(batch.mkString(","))
    stmt.close()
    updatedInstruments
  }

  def executeQuery(query : String) : ResultSet = {
    if (isClosed)
      throw new IllegalStateException(this+ " is closed")
    val stmt : Statement = connection.createStatement()
    val rs : ResultSet = stmt.executeQuery(query)

    val cachedRowset : CachedRowSet = new CachedRowSetImpl()
    cachedRowset.populate(rs)

    stmt.close()
    cachedRowset
  }

  def getCandleSticks(instrument: String, startTime: Long, endTime: Long) : List[Candlestick] = {

    val id = getId(instrument)
    if (id < 0)
      throw new IllegalStateException("Unknown instrument "+ instrument)
    val query = "select * from ticks where instrumentId = "+ id+ " and timestamp >= "+ startTime + " and timestamp  <= "+ endTime +";"
    val rs = executeQuery(query);

    val candlesticks = new ListBuffer[Candlestick]();
    while (rs.next()) {

      val overnightPremium : Double = try {
        rs.getDouble("overnightPremium")
      } catch {
        case e : Exception => 0.0
      }

      candlesticks.append(
        Candlestick(instrument,
          rs.getDouble("bidPrice"),
          rs.getDouble("askPrice"),
          rs.getString("leverage"),
          rs.getDouble("initialMargin"),
          rs.getDouble("maintenanceMargin"),
          rs.getLong("timestamp"),
          overnightPremium))
    }

    candlesticks.toList
  }

  def close {
    isClosed = true
    connection.close()
  }

  def getId(instrument: String) : Int = idMap.getOrElse(instrument, -1)

  override def toString : String = "Loader "+ dbFile.toString
}

object Loader {
  val TABLE_NAMES_SELECT_STMT = "select * from sqlite_master where type='table';"
  val CREATE_METADATA_TABLE_STMT = "create table instruments (id integer primary key autoincrement, instrument text not null, description text not null);"
  val CREATE_TICKS_TABLE_STMT =  "create table ticks (timestamp integer not null, instrumentId integer not null, bidPrice double not null, askPrice double not null, leverage text not null, initialMargin double not null, maintenanceMargin double not null, overnightPremium double);";



  def merge(left: File, right: File, merged: File)
  {
    if (merged.isDirectory)
      throw new IllegalArgumentException("Merged file parameter "+ merged +" must not be a directory")
    if (merged.exists())
      merged.delete()
    if (! merged.createNewFile())
      throw new IllegalArgumentException("Could not create merged file "+ merged)

    val leftLoader = new Loader(left)
    val rightLoader = new Loader(right)
    val mergedLoader = new Loader(merged)

    val instruments = XTL.getSymbols().map(_.instrument)

    for (instrument <- instruments)
    {
      val leftTicks = leftLoader.getCandleSticks(instrument, 0, Long.MaxValue)
      val rightTicks = rightLoader.getCandleSticks(instrument, 0, Long.MaxValue)
      //select unique ticks
      val tickSet = (leftTicks ++ rightTicks).toSet
      if (! tickSet.isEmpty)
        mergedLoader.updateValues(tickSet.toList)
    }
  }

  def main(args: scala.Array[String])
  {
    val toFile = (s: String) => new File(s)
    Loader.merge(toFile(args(0)), toFile(args(1)), toFile(args(2)))
  }
}

case class MetaDataRow(id: Int, symbol: Symbol)