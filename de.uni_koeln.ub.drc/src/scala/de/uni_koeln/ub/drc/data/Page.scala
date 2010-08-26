/**************************************************************************************************
 * Copyright (c) 2010 Fabian Steeg. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 * <p/>
 * Contributors: Fabian Steeg - initial API and implementation
 *************************************************************************************************/

package de.uni_koeln.ub.drc.data

import Db._
import scala.xml._
import java.io._
import java.util.zip._
import de.uni_koeln.ub.drc.reader.Point

/**
 * Representation of a scanned page.
 * @param words The list of words this page consists of
 * @param id An ID for this page (TODO: update to e.g. URI)
 * @author Fabian Steeg
 */
case class Page(words:List[Word], id: String) {
  
  var imageBytes: Option[Array[Byte]] = None
  
  def toXml = <page> { words.toList.map(_.toXml) } </page>
  
  def toText(delim: String) = 
    ("" /: words) (_ + " " + _.history.top.form.replace(Page.ParagraphMarker, delim)) 
  
  def format(root:Node) = {
    val formatted = new StringBuilder
    new PrettyPrinter(120, 2).format(root, formatted)
    formatted
  }
  
  def saveToDb(): Node = {
    val file = id.split("/").last
    val collection = file.split("-")(0)
    val entry = file
    val dbRes = Db.get(collection, entry)
    val mergedPage = mergedDbVersion(dbRes, entry)
    val root = mergedPage.toXml
    val formatted = format(root)
    Db.put(formatted, collection, entry, DataType.XML)
    root
  }
  
  def mergedDbVersion(dbRes:List[Object], entry:String) =
    if(dbRes==null) this else  {
      val dbXml:String = dbRes(0).asInstanceOf[String]
      val dbEntry = Page.load(dbXml, entry)
      Page.mergePages(dbEntry, this)
    }
  
}

object Page {
  
  val ParagraphMarker = "@"

  def fromXml(page:Node, id: String): Page = Page( 
    for(word <- (page \ "word").toList) yield Word.fromXml(word), id
  )
  
  def fromPdf(pdf:String): Page = { PdfToPage.convert(pdf) }
  
  def load(xml:String, id: String): Page = {
      val page:Node = XML.loadString(xml)
      Page.fromXml(page, id)
  }

  /**
   * This models what we get from the OCR: the original word forms as recognized by the OCR,
   * together with their coordinates in the scan result (originally a PDF with absolute values).
   */  
  private val map = Map(
      "daniel" -> Box(130, 283, 150, 30),
      "bonifaci" -> Box(280, 285, 180, 30),
      "catechismus" -> Box(70, 330, 80, 20),
      "als" -> Box(110, 390, 30, 20),
      "slaunt" -> Box(78, 498, 45, 15)
  )
  
  /**
   * This models the other part we get from the OCR: the full text, which we need to tokenize and
   * convert into Word objects to be displayed and edited in the UI.
   */
  val mock: Page = 
    Page(
      for( w <- "Daniel Bonifaci Catechismus Als Slaunt".split(" ").toList ) 
        yield Word(w, map(w.toLowerCase)), "testing-mock"
    )
    
  /** 
   * @param lists The lists of pages to merge (each independently edited, e.g. by different users)
   * @return A single list of pages containing the merged content 
   */
  def merge(lists:List[Page]*):Seq[Page] = {
    for(p1 <- lists.head; list2 <- lists.tail; p2 <- list2; if p1.id == p2.id)
      mergePages(p1, p2)
    lists.head
  }
  
  private def mergePages(p1:Page, p2:Page):Page = {
    for(
        w1 <- p1.words; w2 <- p2.words; m <- w2.history.reverse; // TODO ID for words?
        if(w1.original==w2.original && w1.position == w2.position && (!w1.history.contains(m)))){
      w1.history.push(m)
    }
    p1
  }
  
}

/** 
 *  Experimental heuristics for creating an XML page representation from a scanned PDF.
 *  Includes computation of the highlighting box based on line start coordinated read from the PDF.
 *  @author Fabian Steeg (fsteeg) 
 */
private object PdfToPage {
   
  import java.net.URL
  import de.uni_koeln.ub.drc.reader._
  import scala.collection.JavaConversions._
  import scala.collection.mutable.Buffer
  import java.io.File
    
  def convert(pdfLocation : String) : Page = {
    val words: Buffer[Word] = Buffer()
    val paragraphs : Buffer[Paragraph] = PdfContentExtractor.extractContentFromPdf(pdfLocation).getParagraphs
    val pageHeight = 1440 // IMG_SIZE
    val pageWidth = 900 // IMG_SIZE
    for(p <- paragraphs) {
      for(word <- p.getWords) {
        var startPos = word.getStartPointScaled(pageWidth, pageHeight)
        var endPos = word.getEndPointScaled(pageWidth, pageHeight)
        val scaled = word.getFontSizeScaled(pageHeight)
        val wordWidth = endPos.getX - startPos.getX//width(word.getText, scaled) 
        words add Word(word.getText, Box(startPos.getX.toInt, startPos.getY.toInt - scaled, wordWidth.toInt, scaled))
      }
      words add Word(Page.ParagraphMarker, Box(0,0,0,0))
    }
    Page(words.toList, new java.io.File(pdfLocation).getName().replace("pdf", "xml"))
  }
}
