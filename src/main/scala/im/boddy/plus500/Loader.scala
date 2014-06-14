package im.boddy.plus500

import com.sun.rowset.CachedRowSetImpl;
import javax.sql.rowset.CachedRowSet;

import java.io.File;
import java.sql._
;

/**
 * Created by chris on 6/12/14.
 */
class Loader (private val dbFile: File) {

  try
  {
    Class.forName("org.sqlite.JDBC");
  } catch {
    case cnfe : ClassNotFoundException => throw new SQLException(cnfe);
  }

  val url = "jdbc:sqlite:"+dbFile.getAbsolutePath();
  private val connection  = DriverManager.getConnection(url);
  connection.setAutoCommit(false);
  private val symbols = XTL.getSymbols()

  init
  var isClosed = false

  private def init {
    val rs  = executeQuery(Loader.TABLE_NAMES_SELECT_STMT);

    val presentTables  = new collection.mutable.ListBuffer[String]()
    while (rs.next())
      presentTables += rs.getString("name");

    if (! presentTables.contains("instruments"))
      executeUpdate(Loader.CREATE_METADATA_TABLE_STMT)


    val rss = executeQuery("select * from instruments;")

    val presentMetadataRows  = new collection.mutable.ListBuffer[String]()
    while (rss.next())
      presentMetadataRows += rss.getString("tableName")


    for (symbol <-  symbols)
    {
      //println("symbol " + symbol)
      val tableName = Loader.getTableName(symbol.instrument)
      if (! presentMetadataRows.contains(tableName))
      {
        println("updating symbol "+ symbol +" in instruments")
        val insert = "insert into instruments (tableName, instrument, description) VALUES ('"+ tableName + "', '"+ symbol.instrument+"', '"+ symbol.description+"');"
        executeUpdate(insert)

      }
      if (! presentTables.contains(tableName)) {
        println("creating table "+ tableName)
        executeUpdate(Loader.createInstrumentTableStatement(symbol.instrument))
      }
    }
  }

  private def executeUpdate(update : String, commit :Boolean = true) {
    if (isClosed)
      throw new IllegalStateException(this+" is closed")

    val stmt : Statement = connection.createStatement()
    stmt.executeUpdate(update)
    stmt.close()
    if (commit)
      connection.commit()
  }

  def updateValues(candlesticks : Seq[CandleStick]) = {
    val instruments = symbols.map(_.instrument)

    val updatedInstruments : Seq[String] = {
      if (isClosed)
        throw new IllegalStateException(this + " is closed")

      (for (candlestick <- candlesticks) yield {
        if (! instruments.contains(candlestick.instrument)) {
          println("instrument " + Loader.getTableName(candlestick.instrument) + " does not exist in instruments " + instruments);
          None
        }
        else {
          /*
          //val stmt = connection.prepareStatement("insert into " + Loader.getTableName(candlestick.instrument) + " (timestamp, bidPrice, askPrice, leverage, initialMargin, maintenanceMargin) VALUES(?, ?, ?, ?, ?, ?));

          stmt.setLong(1, candlestick.timestamp);
          stmt.setDouble(2, candlestick.bidPrice);
          stmt.setDouble(3, candlestick.askPrice);
          stmt.setString(4, candlestick.leverage);
          stmt.setDouble(5, candlestick.initialMargin);
          stmt.setDouble(6, candlestick.maintenanceMargin);
          stmt.executeUpdate();
          */
          val update = "insert into "+ Loader.getTableName(candlestick.instrument) + "(timestamp, bidPrice, askPrice, leverage, initialMargin, maintenanceMargin) VALUES(" + candlestick.timestamp +", "+ candlestick.bidPrice +", "+ candlestick.askPrice +", '"+candlestick.leverage +"', "+ candlestick.initialMargin +", "+ candlestick.maintenanceMargin +");"
          executeUpdate(update)
          Some(candlestick.instrument)
        }
      }).flatten
    }


    //stmt.close();
    connection.commit();
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

  def close {
    isClosed = true
    connection.close()
  }

  override def toString : String = "Loader "+ dbFile.toString
}

object Loader{
  val TABLE_NAMES_SELECT_STMT = "select * from sqlite_master where type='table';"
  val CREATE_METADATA_TABLE_STMT = "create table instruments (tableName text not null, instrument text primary key not null, description text not null);"

  def getTableName(instrument: String) = instrument.replaceAll("\\.","_").replaceAll("-","_")
  def createInstrumentTableStatement(instrument: String) =  "create table "+ getTableName(instrument) +" (timestamp integer primary key, bidPrice double, askPrice double, leverage text, initialMargin double, maintenanceMargin double);";
}