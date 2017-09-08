package wikipedia

import java.util

import it.cnr.isti.hpc.wikipedia.article.{Article, Link}
import it.cnr.isti.hpc.wikipedia.parser.ArticleParser

import scala.xml.XML
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import org.apache.log4j.LogManager

// import info.bliki.wiki.dump.WikiArticle
import it.cnr.isti.hpc.wikipedia.article.Article.Type



/**
  * Created by thomasopsomer on 05/09/2017.
  */

class MyArticle extends Article {
  override def toString = "myarticle"
}

/*
 * Mainly a wrapper around the json-wikipedia ArticleParser
 * Use their parser to fill a WikiArticle case class to be
 * used in spark in a dataframe / dataset.
 */

class Parser(lang:String = "en") extends Serializable {

  @transient lazy val logger = LogManager.getLogger("SparkWikipediaParser.Parser")

  private lazy val textParser = new ArticleParser(lang)

  // see https://en.wikipedia.org/wiki/Wikipedia:Namespace
  val typeMap: Map[Int, String] = Map(
    0 -> "Article",
    2 -> "User",
    4 -> "Project",
    6 -> "File",
    10 -> "Template",
    12 -> "Help",
    14 -> "Category",
    100 -> "Portal",
    108 -> "Book",
    118 -> "Draft",
    446 -> "Unknown",
    710 -> "Unknown",
    828 -> "Module",
    2300 -> "Gadget",
    2302 -> "Gadget"
  )

  /*
   * Parse a single xml as a string representing a wikipedia
   * article from the dump
   */
  def parse(xmlStr: String) = {

    val xml = XML.loadString(xmlStr)


    // parse title and ns in xml
    val title = (xml \\ "title").text
    val ns = (xml \\ "ns").text.toInt
    val text = (xml \\ "revision" \\ "text").text

    // parse txt with json-wikipedia parser
    val a = new Article()
    a.setTitle(title)
    a.setIntegerNamespace(ns)
    a.setType(getType(ns))

    try {
      textParser.parse(a, text)
    } catch {
      case e: java.lang.StringIndexOutOfBoundsException =>
        logger.warn(f"Got a StringIndexOutOfBoundsException on article $title")
    }

    // jointly get paragraph and links
    val paralink = mapParagraphsLinks(a)

    // check for wrong disambiguation which are instead Redirection
    var finalType = getFinalTypeName(a)
    val redirect = if (a.getRedirect == "") null else a.getRedirect
    if (isWrongDisamb(paralink._2, finalType) && redirect != null) {
      logger.info(
        f"Setting 'Redirect' instead of 'Disambiguation' for article: $title because it has only one link")
      finalType = "Redirect"
    }

    // check for article that are disambiguation
    if (finalType == "Article" && articleIsDisamb(paralink._1)) {
      logger.info(
        f"Setting 'Redirect' Disambiguation of 'Article' for article: $title because of 'may refer to' parttern")
      finalType = "Disambiguation"
    }

    // fill our wiki article obj :)
    WikiArticle(
      categories = mapLinks(Option(a.getCategories)),
      externalLinks = mapLinks(Option(a.getExternalLinks)),
      highlights = a.getHighlights.asScala.toList,
      integerNamespace = ns,
      lang = a.getLang,
      links = paralink._2,
      paragraphs = paralink._1,
      redirect = redirect,
      title = title,
      wikiTitle = title.replace(" ", "_"),
      `type` = finalType
    )

  }

  /*
   * Transform java list of Link to a scala list of WikiLink
   */
  def mapLinks(list: Option[util.List[Link]]): List[WikiLink] = {
    list match {
      case Some(l) =>
        l.asScala.map(x => {
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
                paragraphs += p
                links += mapLinks(Option(x.getLinks))
              }
            case _ =>
          }
        }
        (paragraphs.toList, links.toList)
      case None => (null, null)
    }
  }

  def getType(ns: Int): Type = {
    ns match {
      case 0 => Type.ARTICLE
      case 2 => Type.USER
      case 4 => Type.PROJECT
      case 6 => Type.FILE
      case 10 => Type.TEMPLATE
      case 12 => Type.HELP
      case 13 => Type.HELPTALK
      case 14 => Type.CATEGORY
      case _ => Type.UNKNOWN
    }
  }

  def getFinalTypeName(a: Article) = {
    val t = a.getType
    t match {
      case Type.UNKNOWN => typeMap.getOrElse(a.getIntegerNamespace, "Unknown")
      case Type.HELP => typeMap.getOrElse(a.getIntegerNamespace, "Unknown")
      case _ => a.getTypeName
    }
  }

  /*
   * Check for wrong disambiguation page that are actually redirection
   * based on number of links
   */
  def isWrongDisamb(links: List[List[WikiLink]], `type`: String) = {
    Option(links) match {
      case Some(l) =>
        if (l.flatten.size == 1 & `type` == "Disambiguation") {
          true
        } else {
          false
        }
      case _ => false
    }
  }

  def articleIsDisamb(paragraphs: List[String], thresh: Int=110): Boolean = {
    if (paragraphs != null) {
      if (paragraphs.nonEmpty) {
        if (paragraphs.head.contains("may refer to") && paragraphs.head.length() < thresh) {
          return true
        }
      }
    }
    false
  }

}
