package com.fotopedia.LogstashLight

import redis.clients.jedis.Jedis

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node._
import com.fasterxml.jackson.databind._

import wabisabi._

import scala.concurrent.duration._
import scala.concurrent.Await

import org.joda.time.Instant
import java.util.Calendar
import java.util.GregorianCalendar

import collection.JavaConversions._

import org.elasticsearch.node.NodeBuilder._
import org.elasticsearch.common.settings._


object LogstashLight {
    def timestamp_ms(logLine:JsonNode, from:String, to:String) {
      val fields = logLine.get("@fields")
      val date = fields.get(from)
      if (date != null) {
        val jInstant = new Instant(date.longValue)
        logLine.asInstanceOf[ObjectNode].set(to, new TextNode(jInstant.toString))
      }

    }
    def rename(logLine: JsonNode, from:String, to:String) {
      val fields = logLine.get("@fields")
      val source = fields.asInstanceOf[ObjectNode].remove(from)
      if(source != null) {
        logLine.asInstanceOf[ObjectNode].set(to, source)
      }
    }

    val REDIS_HOST = System.getenv("REDIS_HOST")
    val REDIS_PORT = Option(System.getenv("REDIS_PORT")).getOrElse("6379").toInt
    val REDIS_QUEUE = Option(System.getenv("REDIS_QUEUE")).getOrElse("prod")
    val ES_HOST = System.getenv("ES_HOST")
    // Binary or HTTP transport port depending on your choice of driver
    val ES_PORT = Option(System.getenv("ES_PORT")).getOrElse("9201").toInt

    def main(args:Array[String]) {
      val jedis = new Jedis(REDIS_HOST, REDIS_PORT)
      var simpleClient:Client = null
      var esClient:org.elasticsearch.client.Client = null
      System.out.println(new Instant)
      // CHANGE HERE IF YOU WANT TO USE THE COMPLETE ELASTICSEARCH DRIVER
      if (true) {
        simpleClient = new Client("http://" + ES_HOST + ":" + ES_PORT)
      } else {
        var settings = ImmutableSettings.settingsBuilder().
          put("node.client", true).
          put("http.enabled", false).
          put("discovery.zen.ping.multicast.enabled", false).
          put("discovery.zen.ping.unicast.hosts", ES_HOST + ":" + ES_PORT )
          esClient = nodeBuilder().settings(settings).node.client()
      }
      System.out.println(new Instant)

      val mapper = new ObjectMapper()
      mapper.registerModule(DefaultScalaModule)

      val l = jedis.llen(REDIS_QUEUE)
      System.out.println("Queue has size:" + l )
      var count = 0
      var log_line =  jedis.lpop(REDIS_QUEUE)

      val queueName = REDIS_QUEUE
      var batch = scala.collection.mutable.ArrayBuffer[Object]()

      while(true) {
        count = count + 1

        val logLineObject = mapper.readTree(log_line)
        timestamp_ms(logLineObject, "date", "@timestamp")
        rename(logLineObject, "instance", "host")

        var indexName = String.format("logstash-%1$tY.%1$tm.%1$td.%1$tH", new GregorianCalendar)
        batch += Map[String, Object]("index" -> Map[String, String]("_index" -> indexName, "_type" -> queueName))
        batch += logLineObject

        if (count == 100) {
          System.out.println("Sending 100 objects")
          if (simpleClient != null) {
            val res = Await.result(simpleClient.bulk(data = (batch.map { v => mapper.writeValueAsString(v) }.mkString("\n"))+"\n"), Duration(8, "second")).getResponseBody
          } else {
            val bulkRequest = esClient.prepareBulk();
            // either use client#prepare, or use Requests# to directly build index/delete requests
            batch.foreach { case l:JsonNode => {
                val lAsString = mapper.writeValueAsString(l)
                bulkRequest.add(esClient.prepareIndex(indexName, queueName)
                      .setSource(lAsString))
              }
              case _ =>
            }
            val bulkResponse = bulkRequest.execute().actionGet();
            if (bulkResponse.hasFailures()) {
              System.out.println(bulkResponse.toString())
            }
          }
          System.out.println("done...")
          count = 0
          batch = scala.collection.mutable.ArrayBuffer[Object]()
        }
        log_line = jedis.lpop(queueName)
        while(log_line == null) {
          System.out.println("Exhausted queue, sleeping")
          Thread.sleep(1000)
          log_line = jedis.lpop(queueName)
        }
      }
      System.out.println("Arrived at end.")
    }
}
