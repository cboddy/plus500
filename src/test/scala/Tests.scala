package im.boddy.plus500

import java.io.{BufferedWriter, FileWriter}
import java.nio.file._



import io.Source
/**
 * Created by chris on 6/11/14.
 */
object Tests extends App {

  val instrumentTestFile = Paths.get("data","XAU").toFile

  val instrumentPage = Source.fromFile(instrumentTestFile).mkString

  val candleStick = Transformer.extractCandleStick(instrumentPage, "XAU")

  println(candleStick)


  val instrumentsTestFile = Paths.get("data", "AllInstruments.aspx").toFile

  val symbols = Transformer.extractSymbols(Source.fromFile(instrumentsTestFile).mkString)

  println(symbols)

  val symbolsFile = Paths.get("data","all.symbols.txt").toFile

  val symbolOutput = symbols.map(s => s.instrument +" = "+ s.description).mkString("\n")
  val writer = new BufferedWriter(new FileWriter(symbolsFile))
  try
  {
    writer.write(symbolOutput)
  } catch {
    case e : Exception => e.printStackTrace()
  } finally {
    writer.close()
  }


  val dbFile = Paths.get("data","test_db.db").toFile
  //dbFile.delete()
  //val loader = new Loader(dbFile)
  //loader.close

  val xtl = new XTL(dbFile, nThread = 1)
  val start = System.currentTimeMillis()
  xtl.xtl()
  val duration = System.currentTimeMillis() - start
  println("xtl took "+ duration +" ms.")
  //xtl.close

  xtl.run

}
