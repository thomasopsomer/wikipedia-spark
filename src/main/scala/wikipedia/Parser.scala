package wikipedia

import java.util

import it.cnr.isti.hpc.wikipedia.article.{Article, Link}
import it.cnr.isti.hpc.wikipedia.parser.ArticleParser

import scala.xml.XML
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer


/**
  * Created by thomasopsomer on 05/09/2017.
  */

class MyArticle extends Article {
  override def toString = "myarticle"
}

/*
 * Mainly a wrapper around the json-wikipedia ArticleParser
 * Use their wrapper to fill a WikiArticle case class to be
 * used in spark in a dataframe / dataset.
 */

class Parser(lang:String = "en") extends Serializable {

  lazy val textParser = new ArticleParser(lang)

  /*
   * Parse a single xml as a string representing a wikipedia
   * article from the dump
   */
  def parse(xmlStr: String) = {

    val xml = XML.loadString(xmlStr)
    val text = (xml \\ "revision" \\ "text").text
    val a = new Article()
    textParser.parse(a, text)

    // parse title and ns in xml
    val title = (xml \\ "title").text
    val ns = (xml \\ "ns").text.toInt

    // jointly get paragraph and links
    val paralink = mapParagraphsLinks(a)

    // fill our wiki article obj :)
    WikiArticle(
      categories = mapLinks(Option(a.getCategories)),
      externalLinks = mapLinks(Option(a.getExternalLinks)),
      highlights = a.getHighlights.asScala.toList,
      integerNamespace = ns,
      lang = a.getLang,
      links = paralink._2,
      paragraphs = paralink._1,
      redirect = if (a.getRedirect == "") null else a.getRedirect,
      title = title,
      wikiTitle = title.replace(" ", "_"),
      `type` = a.getTypeName
    )
  }

  /*
   * Transform java list of Link to a scala list of WikiLink
   */
  def mapLinks(list: Option[util.List[Link]]): List[WikiLink] = {
    list match {
      case Some(l) =>
        l.asScala.map( x => {
          WikiLink(
            anchor = x.getAnchor,
            id = x.getCleanId,
            start = x.getStart,
            end = x.getEnd
          )
        }).toList
      case None => null
    }
  }

  /*
   * Use the getParagraphsWithLinks from idio/json-wikipedia to
   * jointly retrieve paragraph and links with their character offset
   */
  def mapParagraphsLinks(a: Article): (List[String], List[List[WikiLink]]) = {
    var links = ArrayBuffer.empty[List[WikiLink]]
    var paragraphs = ArrayBuffer.empty[String]

    Option(a.getParagraphsWithLinks) match {
      case Some(paraWithLinks) =>
        for (x <- paraWithLinks.asScala) {
          val para = Option(x.getParagraph)
          para match {
            case Some(p) =>
              if (!p.trim.isEmpty) {
                paragraphs += p.trim
                links += mapLinks(Option(x.getLinks))
              }
            case _ =>
          }
        }
        (paragraphs.toList, links.toList)
      case None => (null, null)
    }
  }

}
