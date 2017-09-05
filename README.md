# wikipedia-spark

This repo is mainly a wrapper of [idio/json-wikipedia](https://github.com/idio/json-wikipedia), which itself bring some improvement over [diegoceccarelli/json-wikipedia](https://github.com/diegoceccarelli/json-wikipedia).

It combines [databricks/spark-xml](https://github.com/databricks/spark-xml) to read wikipedia dump (without decompressing the whole files :) and the `ArticleParser` from json-wikipedia to parse wikipedia articles. It brings the speed from Spark and the ability to save easily in parquet or json format, in a distributed maner for instance on S3 :)


## build

1. Build json-wikipedia using the command `mvn assembly:assembly` (for more information see their [readme](https://github.com/idio/json-wikipedia/blob/development/README.md))

Actually you shoud remove the spark dependency from the `pom.xml` because it's big and is still at version 1.3 ...

Once the `json-wikipedia-1.2.0-jar-with-dependencies.jar` is build, put it in a `lib` folder at the root of this repo.


2. Build wikipedia-spark using `sbt assembly` or `sbt docker` (which also builds you an image ready to go)


## usage

```bash
docker run --rm asgard/wikipedia-spark:latest spark-submit /home/wikipedia-spark/wikipedia-spark-assembly-0.0.1.jar ...
```

```
Usage: SparkWikipediaParser [options]

  --inputPath <value>
        path to xml dump or folder with xml dumps files
  --outputPath <value>
        path to output file / folder
  --outputFormat <value>
        Format for the ouput: parquet or json
  --lang <value>
        language of the dump
  --test
        Flag to test on a few lines
```





