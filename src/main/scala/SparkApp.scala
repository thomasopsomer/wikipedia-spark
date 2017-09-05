import com.databricks.spark.xml.XmlInputFormat
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import wikipedia.{Parser, WikiArticle}
import scopt.OptionParser

/**
  * Created by thomasopsomer on 05/09/2017.
  */
object SparkApp {

  case class Params(
                     inputPath: String = null,
                     outputPath: String = null,
                     outputFormat: String = "parquet",
                     lang: String = "en",
                     test: Boolean = false
                   )

  lazy val conf = new SparkConf()
    .setAppName("WikipediaParser")
    .setMaster("local[*]")
    .set("spark.driver.allowMultipleContexts", "true")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .set("spark.sql.parquet.compression.codec", "gzip")

  lazy val spark = SparkSession
    .builder()
    .config(conf = conf)
    .getOrCreate()

  def run(params: Params) = {

    // For implicit conversions like converting RDDs to DataFrames
    import spark.implicits._

    // This will detect the tags including attributes
    spark.sparkContext.hadoopConfiguration.set(XmlInputFormat.START_TAG_KEY, "<page>")
    spark.sparkContext.hadoopConfiguration.set(XmlInputFormat.END_TAG_KEY, "</page>")
    spark.sparkContext.hadoopConfiguration.set(XmlInputFormat.ENCODING_KEY, "utf-8")
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.connection.timeout", "500000")
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.connection.maximum", "100")

    // make a RDD of xml string, one article per line using databricks
    // xml spark reader
    var dumpRdd: RDD[(LongWritable, Text)] = spark.sparkContext.newAPIHadoopFile(
      params.inputPath,
      classOf[XmlInputFormat],
      classOf[LongWritable],
      classOf[Text])

    if (params.test) {
      dumpRdd = spark.sparkContext.parallelize(dumpRdd.take(1000))
    }

    // init our wikipedia parser
    val parser = new Parser(lang=params.lang)

    // parse the dump
    val dumpDS: Dataset[WikiArticle] = dumpRdd.map(x => parser.parse(x._2.toString)).toDS()

    // save as parquet or json
    params.outputFormat match {
      case "parquet" => dumpDS.write.parquet(params.outputPath)
      case "json" => dumpDS.write.json(params.outputPath)
      case _ => println("outputFormat needs to be parquet or json")
    }
  }

  def main(args: Array[String]): Unit = {

    // Argument parser
    val parser = new OptionParser[Params]("SparkWikipediaParser") {
      head("Spark Application that parse wikipedia xml dump using json-wikipedia")

      opt[String]("inputPath").required()
        .text("path to xml dump or folder with xml dumps files")
        .action((x, c) => c.copy(inputPath = x))

      opt[String]("outputPath").required()
        .text("path to output file / folder")
        .action((x, c) => c.copy(outputPath = x))

      opt[String]("outputFormat")
        .text("Format for the ouput: parquet or json")
        .action((x, c) => c.copy(outputFormat = x))

      opt[String]("lang")
        .text("language of the dump")
        .action((x, c) => c.copy(lang = x))

      opt[Unit]("test")
        .text("Flag to test on a few lines")
        .action((_, c) => c.copy(test = true))

    }
    // parser.parse returns Option[C]
    parser.parse(args, Params()) match {
      case Some(params) => run(params)
      case None =>
        parser.showUsageAsError
        sys.exit(1)
    }
  }

}
