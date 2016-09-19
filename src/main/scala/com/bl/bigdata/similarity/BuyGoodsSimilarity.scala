package com.bl.bigdata.similarity

import java.text.SimpleDateFormat
import java.util.Date

import com.bl.bigdata.mail.Message
import com.bl.bigdata.util._
import org.apache.spark.Accumulator
import org.apache.spark.rdd.RDD

/**
  * 计算用户购买的物品在一级类目下的关联度。
  * 物体 A 和 B 的关联度：
  * r(A,B) = N(A,B) / N(B)
  * r(B,A) = N(A,B) / N(A)
  * Created by MK33 on 2016/3/16.
  */
class BuyGoodsSimilarity extends Tool{

  override def run(args: Array[String]): Unit = {
    val optionsMap = BuyGoodsSimConf.parse(args)

    logger.info("买了还买开始计算......")
    // 输出参数
    optionsMap.foreach { case (k, v) => logger.info(k + " : " + v)}
    Message.addMessage("买了还买:")

    val output = optionsMap(BuyGoodsSimConf.output)
    val prefix = optionsMap(BuyGoodsSimConf.goodsIdPrefix)
    val redis = output.contains("redis")
    val sc = SparkFactory.getSparkContext("买了还买")
    val accumulator = sc.accumulator(0)
    val accumulator2 = sc.accumulator(0)


    val limit = optionsMap(BuyGoodsSimConf.days).toInt
    val sdf = new SimpleDateFormat(optionsMap(BuyGoodsSimConf.sdf))
    val date0 = new Date
    val start = sdf.format(new Date(date0.getTime - 24000L * 3600 * limit))

    val BehaviorRawDataRDD = DataBaseUtil.getData(optionsMap(BuyGoodsSimConf.input), optionsMap(BuyGoodsSimConf.sql_1), start)

    val sql = "select category_sid, cookie_id, event_date, behavior_type, goods_sid " +
      " from recommendation.user_behavior_raw_data  " +
      s"where dt >= $start"
//    ReadData.readHive(sc, sql)
    val buyGoodsRDD = BehaviorRawDataRDD.filter(s => s.contains(null)).map { case Array(category, cookie, date, behaviorId, goodsId) =>
                                            (category, (cookie, date.substring(0, date.indexOf(" ")), behaviorId, goodsId)) }
                              .filter(_._2._3 == "4000")
                              .map { case (category, (cookie, date, behaviorID, goodsID)) => (category, (cookie, date, goodsID)) }
                              .distinct()

    val categoryRDD = DataBaseUtil.getData(optionsMap(BuyGoodsSimConf.input), optionsMap(BuyGoodsSimConf.sql_2))

    val sql2 = "select category_id, level2_id from recommendation.dim_category"
//    ReadData.readHive(sc, sql2)
    val categoriesRDD = categoryRDD.filter(s => s.contains(null)).map{ case Array(category, level) => (category, level)}.distinct().map(s => (s._1.toString, s._2.toString))
    // 将商品的末级类别用一级类别替换
    val buyGoodsKindRDD = buyGoodsRDD.join(categoriesRDD)
                                      .map { case (category, ((cookie, date, goodsID), kind)) => ((cookie, date, kind), goodsID) }
    // 统计每种物品的购买数量
    val buyCount = buyGoodsKindRDD.map{ case ((cookie, date, kind), goodsID) => (goodsID, 1)}.reduceByKey(_ + _)
    // 计算用户购买物品在同一类别下的关联度
    val result0 = buyGoodsKindRDD.join(buyGoodsKindRDD)
                                  .filter { case ((cookie, date, kind), (goodsID1, goodsID2)) => goodsID1 != goodsID2 }
                                  .map { case ((cookie, date, kind), (goodsID1, goodsID2)) => ((goodsID1, goodsID2), 1) }
                                  .reduceByKey (_ + _)
                                  .map { case ((goodsID1, goodsID2), count) => (goodsID2, (goodsID1, count)) }
    val result = result0.join(buyCount)
                        .map{ case (goodsID2, ((goodsID1, count), goodsID2Count)) => (goodsID1, goodsID2, count.toDouble / goodsID2Count) }
    val sorting = result.map{ case (goodsID1, goodsID2, similarity) => (goodsID1, Seq((goodsID2, similarity)))}
                        .reduceByKey((s1,s2) => s1 ++ s2)
                        .mapValues(seq => { accumulator += 1; seq.sortWith(_._2 > _._2).map(_._1).mkString("#")})
    sorting.persist()
    if (redis) {
      val redisType = if (output.contains("cluster")) "cluster" else "standalone"
//      saveToRedis(sorting.map(s => (prefix + s._1, s._2)), accumulator2, redisType)
      RedisClient.sparkKVToRedis(sorting.map(s => (prefix + s._1, s._2)), accumulator2, redisType)
      Message.addMessage(s"\tr$prefix*: $accumulator.\n")
      Message.addMessage(s"\t插入 redis $prefix*: $accumulator2.\n")
    }
    sorting.unpersist()
    logger.info("买了还买计算结束。")

  }

  def saveToRedis(rdd: RDD[(String, String)], accumulator: Accumulator[Int], redisType: String): Unit = {
    rdd.foreachPartition(partition => {
      try {
        redisType match {
          case "cluster" =>
            val jedis = RedisClient.jedisCluster
            partition.foreach { s =>
              jedis.set(s._1, s._2)
              accumulator += 1
            }
            jedis.close()
          case "standalone" =>
            val jedis = RedisClient.pool.getResource
            partition.foreach { s =>
              jedis.set(s._1, s._2)
              accumulator += 1
            }
            jedis.close()
          case _ => logger.error(s"wrong redis type $redisType ")
        }
      } catch {
        case e: Exception => Message.addMessage(e.getMessage)
      }
    })
  }


}

object BuyGoodsSimilarity {

  def main(args: Array[String]) {
   execute(args)
  }

  def execute(args: Array[String]): Unit ={
    val buyGoodsSimilarity = new BuyGoodsSimilarity with ToolRunner
    buyGoodsSimilarity.run(args)
  }
}

object BuyGoodsSimConf  {

  val input = "input"
  val sql_1= "sql_1"
  val sql_2 = "sql_2"
  val output = "output"

  val goodsIdPrefix = "prefix"
  val days = "n-days"
  val sdf = "dateFormat"


  val buyGoodsSimCommand = new MyCommandLine("buyGoodsSimilarity")
  buyGoodsSimCommand.addOption("i", input, true, "data.source", "hive")
  buyGoodsSimCommand.addOption("o", output, true, "output data to redis， hbase or HDFS", "redis-cluster")
  buyGoodsSimCommand.addOption(sql_1, sql_1, true, "user.behavior.raw.data", "buy.goods.sim-1")
  buyGoodsSimCommand.addOption(sql_2, sql_2, true, "dim.category", "buy.goods.sim-2")
  buyGoodsSimCommand.addOption("p", goodsIdPrefix, true, "goods id prefix", "rcmd_bab_goods_")
  buyGoodsSimCommand.addOption("n", days, true, "days before today", "90")
  buyGoodsSimCommand.addOption("sdf", sdf, true, "date format", "yyyyMMdd")

  def parse(args: Array[String]): Map[String, String] ={
    buyGoodsSimCommand.parser(args)
  }



}