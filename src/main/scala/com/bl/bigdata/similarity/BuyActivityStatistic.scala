package com.bl.bigdata.similarity

import java.text.SimpleDateFormat
import java.util.Date

import com.bl.bigdata.datasource.ReadData
import com.bl.bigdata.mail.Message
import com.bl.bigdata.util._

/**
  * 1. 统计上午、下午、晚上 购买类目top 20
  * 2. 订单里面分类目单价低于20元top20商品
  * 3. 分析订单里面购买关联类目，哪些类目同时购买
  *
  * 上午： [08:00-12:00)
  * 下午：[12:00-18:00)
  * 晚上：其余
  * Created by MK33 on 2016/3/18.
  */
class BuyActivityStatistic extends Tool {

  override def run(args: Array[String]): Unit = {
    logger.info("上午、下午、晚上购买类目开始计算......")
    Message.addMessage("上午 下午 晚上 购买类目:\n")

    val outputPath = ConfigurationBL.get("recmd.output")
    val redis = outputPath.contains("redis")
    val num = ConfigurationBL.get("buy.activity.category.topNum").toInt
    val sc = SparkFactory.getSparkContext("上午 下午 晚上 购买类目")
    val limit = ConfigurationBL.get("day.before.today", "90").toInt
    val sdf = new SimpleDateFormat("yyyyMMdd")
    val date = new Date
    val start = sdf.format(new Date(date.getTime - 24000L * 3600 * limit))
    val sql = "select category_sid, event_date, behavior_type from recommendation.user_behavior_raw_data  " +
      s"where dt >= $start"
    val rawRDD = ReadData.readHive(sc, sql)
                          .map{ case Array(category, date1, behavior) =>
                                    (category, date1.substring(date1.indexOf(" ") + 1), behavior)}
                          .filter{ case (category, time, behavior) => behavior.equals("4000") && !category.equalsIgnoreCase("NULL")}
                          .map{ case (category, time, behavior) => (category, time)}

    // 统计上午、下午、晚上 购买类目top 10
    val morning = rawRDD.filter{ case (category, time) => time >= "08" & time < "12:00:00.0"}
                        .map{ s => (s._1, 1)}
                        .reduceByKey(_ + _)
                        .sortBy(_._2, ascending = false)
                        .take(num).map(_._1).mkString("#")

    val noon = rawRDD.filter{ case (category, time) => time >= "12" & time < "18:00:00.0"}
                      .map{ s => (s._1, 1)}
                      .reduceByKey(_ + _)
                      .sortBy(_._2, ascending = false)
                      .take(num).map(_._1).mkString("#")

    val evening = rawRDD.filter{ case (category, time) => time >= "18" | time <= "08"}
                        .map{ s => (s._1, 1)}
                        .reduceByKey(_ + _)
                        .sortBy(_._2, ascending = false)
                        .take(num).map(_._1).mkString("#")

    if (redis){
//      val jedis = new Jedis(ConfigurationBL.get("redis.host"), ConfigurationBL.get("redis.port", "6379").toInt)
      val jedisCluster = RedisClient.jedisCluster
      jedisCluster.set("rcmd_topcategory_forenoon", morning)
      jedisCluster.set("rcmd_topcategory_afternoon", noon)
      jedisCluster.set("rcmd_topcategory_evening", evening)
//      jedis.close()
      Message.addMessage(s"\t上午:\n\t\t$morning\n")
      Message.addMessage(s"\t中午:\n\t\t$noon\n")
      Message.addMessage(s"\t晚上:\n\t\t$evening\n")
    }
    logger.info("上午、下午、晚上购买类目计算结束。")
  }
}

object BuyActivityStatistic {

  def main(args: Array[String]): Unit = {
    execute(args)
  }

  def execute(args: Array[String]): Unit ={
    val buyActivityStatistic = new BuyActivityStatistic with ToolRunner
    buyActivityStatistic.run(args)
  }
}