# Elasticsearch OpenNLP Plugin

**DO NOT USE THIS ANYMORE, IT IS DEPRECATED AND A BAD DESIGN IDEA**

* This PoC was against a really 0.90 version of Elasticsearch, porting it to newer version would need some significant amount of work
* NLP enrichment in general is clearly a preprocessing step, that should not be done in Elasticsearch itself. First, the NLP model needs to be loaded in all nodes, requiring you a significant amount of heap to dedicate to NLP instead of Elasticsearch, destabilizing Elasticsearch
* Upgrading your model requires a restart of all of the Elasticsearch nodes, resulting in unwanted downtime.
* Workaround 1: You could have your own service in front of Elasticsearch that is doing NLP enrichments before sending the document to Elasticsearch. This one is decoupled and can be updated anytime, and even scaled up and down independently.
* Workaround 2: Check out the work which is currently (early 2016) being done in the [ingest branch](https://github.com/elastic/elasticsearch/tree/feature/ingest) in Elasticsearch  - that is a mechanism allowing you to change a document before indexing, and this is, where it makes sense to port this NLP plugin to in the future. Also check out the [github issues](https://github.com/elastic/elasticsearch/labels/%3AIngest) around  this topic.

If you are searching for an update on this, you might want to check out the [elasticsearch ingest opennlp processor](https://github.com/spinscale/elasticsearch-ingest-opennlp) for Elasticsearch 5.0 and above

This plugin uses the opennlp project to extract named entities from an indexed field. This means, when a certain field of a document is indexed, you can extract entities like persons, dates and locations from it automatically and store them in additional fields.

Add the configuration

```
opennlp.models.name.file: /path/to/elasticsearch-0.20.5/models/en-ner-person.bin
opennlp.models.date.file: /path/to/elasticsearch-0.20.5/models/en-ner-date.bin
opennlp.models.location.file: /path/to/elasticsearch-0.20.5/models/en-ner-location.bin
```

Add a mapping

```
curl -X PUT localhost:9200/articles
curl -v http://localhost:9200/articles/article/_mapping -d '
{ "article" : { "properties" : { "content" : { "type" : "opennlp" } } } }'
```

Index a document

```
curl -X PUT http://localhost:9200/articles/article/1 -d '
{ "title" : "Some title" ,
"content" : "Kobe Bryant is one of the best basketball players of all times. Not even Michael Jordan has ever scored 81 points in one game." }'
```

Query for a persons name

```
curl -X POST http://localhost:9200/articles/article/_search -d '
{ "query" : { "term" : { "content.name" : "kobe" } } }'
```

Query for another part of the article and you will not find it

```
curl -X POST http://localhost:9200/articles/article/_search -d '
{ "query" : { "term" : { "content.name" : "basketball" } } }'
```

Querying also works for locations or dates

```
curl -X PUT http://localhost:9200/articles/article/2 -d '{ "content" : "My next travel destination is going to be in Amsterdam. I will be going to Schiphol Airport next Sunday." }'

curl -X POST http://localhost:9200/articles/article/_search -d '
{ "query" : { "text" : { "content.location" : "schiphol airport" } } }'

curl -X POST http://localhost:9200/articles/article/_search -d '
{ "query" : { "term" : { "content.location" : "amsterdam" } } }'

curl -X POST http://localhost:9200/articles/article/_search -d '
{ "query" : { "term" : { "content.date" : "sunday" } } }'
```

Facetting is supported as well

```
curl -X PUT localhost:9200/articles
curl -v http://localhost:9200/articles/article/_mapping -d '{ "article" : { "properties" : { "content" : { "type" : "opennlp", "location_analyzer" : "keyword" } } } }'
curl -X PUT http://localhost:9200/articles/article/2 -d '{ "content" : "My next travel destination is going to be in Amsterdam. I will be going to Schiphol Airport next Sunday." }'
curl -X POST http://localhost:9200/articles/article/_search -d '{ "query" : { "match_all" : {} }, "facets" : { "location" : { "terms" : { "field" : "content.location" }} }, "size" : 0 }'

```


## Downloading the models

In case you want to run the tests or use the plugin in elasticsearch, you need to download the models from [sourceforge](http://opennlp.sourceforge.net/models-1.5/).

```
wget http://opennlp.sourceforge.net/models-1.5/en-ner-person.bin
wget http://opennlp.sourceforge.net/models-1.5/en-ner-location.bin
wget http://opennlp.sourceforge.net/models-1.5/en-ner-date.bin
```

Copy these models somewhere in our filesystem.


## Installation

Package the plugin by calling `mvn package -DskipTests=true` and then install the plugin via

```
bin/plugin -install opennlp -url file:///path/to/elasticsearch-opennlp-plugin/target/releases/elasticsearch-plugin-opennlp-0.1-SNAPSHOT.zip
```


### Running the tests

In case you want to run the tests, copy the above downloaded models to `src/test/resources/models` and run `mvn clean package`


## Mapping configuration

If you want to enable any field for NLP parsing, you need to set it via mapping, similar to the [elasticsearch attachments mapper](https://github.com/elasticsearch/elasticsearch-mapper-attachments/) plugin.

```
{
  article:{
    properties:{
      "content" : { "type" : "opennlp" }
    }
  }
}
```


### Using different analyzers per field

You can also use different analyzers per field, if you want (it might not make sense to use the default analyzer for dates for example)

```
{
  article:{
    properties:{
      "content" : {
        "type" : "opennlp",
        "person_analyzer"   : "your_own_analyzer",
        "date_analyzer"     : "your_next_analyzer",
        "location_analyzer" : "your_other_analyzer"
      }
    }
  }
}
```


# Problems & considerations

* The whole NLP process is pretty RAM costly, consider this when starting elasticsearch
* Bad architecture, the `OpenNlpService` uses final variables instead of being built correctly. I am not too proud of it, but it works and I hacked it up in a few days of prototyping.


# Future directions


## Supporting different taggers

My first implementation was using a POS tagger, but this only yielded some grammatical content, so a tagger should be more capable. But perhaps other people could make use of that in different use cases.


## Supporting more languages

Currently only english is supported.


## Suggestions

Another interesting thing would be to support this fields with suggestions, so you could have a "name" input field in your application, which would suggest only the names of a field. For example you have a set of news articles, where you are searching in names only (very good, if a the person you are searching for a person surnamed "Good").

If you have an article which contains "Kobe Bryant" and "Michal Jordan", you should want to suggest them properly. This currently does not work, because the extracted names are simply appended to one string in the field.


# Credits

Some code has been copied from the [Taming Text book](http://tamingtext.com/) and its [sources](https://github.com/tamingtext/book). In case you want an engineering driven introduction into this topic, I highly recommend this book.


# License

The plugin is licensed under Apache 2.0 License.
