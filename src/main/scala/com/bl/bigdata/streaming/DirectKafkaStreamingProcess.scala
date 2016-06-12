package com.bl.bigdata.streaming

import java.io.{FileInputStream, InputStreamReader}
import java.util.Properties

import akka.actor.Actor
import com.bl.bigdata.util.RedisClient
import kafka.common.TopicAndPartition
import kafka.serializer.StringDecoder
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.util.Bytes
import org.apache.kafka.clients.consumer.{KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka.{HasOffsetRanges, KafkaUtils, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.json.JSONObject

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
 * Created by MK33 on 2016/5/25.
 */
object DirectKafkaStreamingProcess extends StreamingLogger {


  def main(args: Array[String]) {
    logger.debug("==========  program start!  ==========")

    val streamingProperties = new Properties()
    streamingProperties.load(new InputStreamReader(new FileInputStream("streaming.properties")))

    val kafkaProps = new Properties()
    kafkaProps.load(new InputStreamReader(new FileInputStream("kafka.properties")))

    val kafkaParams = mutable.Map[String, String]()
    val keys = kafkaProps.keySet().iterator()
    while (keys.hasNext) {
      val key = keys.next().toString
      kafkaParams(key) = kafkaProps.getProperty(key)
    }
//    val topic = streamingProperties.getProperty("kafka.topic.subscribe").split(",").toSet
//    val sc = new SparkContext(new SparkConf())
//    val ssc = new StreamingContext(sc, Seconds(streamingProperties.getProperty("streaming.batch.interval").toInt))
//    val consumer = new KafkaConsumer[String, String](kafkaParams) with Serializable
//    consumer.subscribe(topic.toList)
//    val ssc = StreamingContext.getOrCreate(checkpointPath, () => createStreamingContext(streamingProperties, checkpointPath, kafkaParams.toMap))
    val ssc = new StreamingContext(new SparkConf().setAppName("kafka-streaming-direct"), Seconds(2))
    val topic = streamingProperties.getProperty("kafka.topic.subscribe").split(",").toSet
    val dStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams.toMap, topic)

    var offsetRanges = Array[OffsetRange]()
    dStream.transform { rdd =>
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    }.map(_._2).foreachRDD {
      rdd =>
        offsetRanges.foreach { o =>
          logger.debug(s"topic: ${o.topic}, partition: ${o.partition}, fromOffset: ${o.fromOffset}, untilOffset: ${o.untilOffset}") }
        rdd.foreachPartition
        { partition =>
            val conn = HBaseConnectionPool.connection
            // val conn = ConnectionFactory.createConnection(conf)
            val table = conn.getTable(TableName.valueOf("kafka-streaming-test"))
            val jedis = RedisClient.pool.getResource
            partition.foreach
            { item =>
              try {
                val json = new JSONObject(item)
                val behaviorType = json.getString("actType")
                val cookie = json.getString("cookieId")
                val eventDate = json.getString("eventDate")
                val goodsId = json.getString("goodsId")
                val get = new Get(Bytes.toBytes(cookie))
                if (table.exists(get))
                {
    //              val memberID = jedis.get(cookie)
//                  val value = table.get(get).getValue(Bytes.toBytes("test"), Bytes.toBytes("cookie"))
                  val memberID = new String(table.get(get).getValue(Bytes.toBytes("test"), Bytes.toBytes("cookie")))
                  jedis.hmset("last_view_" + memberID, Map(goodsId -> eventDate))
                  logger.debug(s"save to redis key: last_view_$memberID")
                } else {
                  logger.debug(s"cookie: $cookie not found in hbase")
                }
              } catch {
                case e: Exception => logger.debug(item)
              }

            }
            // 处理完一定要释放连接，否则连接池很快就会被耗尽
            jedis.close()
      }


    }



    ssc.start()
    ssc.awaitTermination()
    logger.info("===============   end  =================")

  }

  def createStreamingContext(conf: Properties, checkpointPath: String, kafkaConf: Map[String, String]): StreamingContext =
  {

    logger.info("creating streaming context")
    val topic = conf.getProperty("kafka.topic.subscribe").split(",").toSet
    val sc = new SparkContext(new SparkConf())
    val ssc = new StreamingContext(sc, Seconds(conf.getProperty("streaming.batch.interval").toInt))
    ssc.checkpoint(checkpointPath)

    val dStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaConf, topic)

    val target = conf.getProperty("target")
    target match
    {
      case "redis" => redis(dStream)
      case "hbase" => logger.debug("Hello world, HBase !")
      case _ => logger.debug("<redis> or <hbase>")
    }
    ssc
  }

  /** 将数据导入 redis */
  def redis[K,V](inputDStream: InputDStream[(String, String)]) =
  {

    var offsetRanges = Array[OffsetRange]()
    val offsetFrom = mutable.Map[TopicAndPartition, Long]()
//    val topicPartitionMap = mutable.Map[TopicPartition, OffsetAndMetadata]()

    inputDStream.transform
    { rdd =>
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    }.map(_._2).foreachRDD
    { rdd =>
      for (o <- offsetRanges)
      {
        logger.debug(s"topic: ${o.topic}, partition: ${o.partition}, fromOffset: ${o.fromOffset}, untilOffset: ${o.untilOffset}")
        offsetFrom(new TopicAndPartition(o.topic, o.partition)) = o.untilOffset
//        topicPartitionMap(new TopicPartition(o.topic, o.partition)) = new OffsetAndMetadata(o.untilOffset, "spark.streaming.test")
      }
      rdd.foreachPartition
      { partition =>
        val jedis = RedisClient.pool.getResource
        partition.foreach
        { item =>
          try {
            val json = new JSONObject(item)
            val behaviorType = json.getString("actType")
            val cookie = json.getString("cookieId")
            val eventDate = json.getString("eventDate")
            val goodsId = json.getString("goodsId")
            if (jedis.exists(cookie))
            {
              val memberID = jedis.get(cookie)
              jedis.hmset("last_view_" + memberID, Map(goodsId -> eventDate))
              logger.debug(s"save to redis key: last_view_$memberID")
            } else {
              logger.debug(s"cookie: $cookie not found in redis")
            }
          } catch {
            case e: Exception => logger.debug(item)
          }

        }
      }
//      offsetActor ! topicPartitionMap
    }
  }

}

class OffsetActor extends Actor with StreamingLogger with Serializable {

  lazy val consumer = {
    val kafkaProps = new Properties()
    kafkaProps.load(new InputStreamReader(new FileInputStream("kafka.properties")))
    new KafkaConsumer(kafkaProps) with Serializable
  }

  override def receive: Receive = {
    case offsetMsg: mutable.Map[TopicPartition, OffsetAndMetadata] => consumer.commitSync(offsetMsg)
    case e => logger.info(e.toString)
  }
}