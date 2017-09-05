package wikipedia

/**
  * Created by thomasopsomer on 05/09/2017.
  */

case class WikiArticle(
                  categories: List[WikiLink] = null,
                  externalLinks: List[WikiLink] = null,
                  highlights: List[String] = null,
                  integerNamespace: Int = 0,
                  lang: String = null,
                  links: List[List[WikiLink]] = null,
                  paragraphs: List[String] = null,
                  redirect: String = null,
                  timestamp: String = null,
                  title: String = null,
                  wikiTitle: String = null,
                  `type`: String = null
                  )

case class WikiLink(
               anchor: String = null,
               id: String = null,
               start: Int = 0,
               end: Int = 0
               )
