package com.bl.bigdata.redis

import java.util
import java.util.{Collections, HashMap}

import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.junit.Test
import redis.clients.jedis.{Jedis, JedisPool}

/**
 * Created by MK33 on 2016/5/18.
 */
@Test
class HMSet {

  @Test
  def test = {

    val conf = new GenericObjectPoolConfig
    conf.setMaxTotal(100)
    lazy val jedis = new JedisPool(conf, "127.0.0.1").getResource
    val map1 = new HashMap[String, String]()
    map1.put("k1-1", "v1-1")
    map1.put("k1-2", "v1-2")
    println("====")
    if (jedis.exists("test")) jedis.del("test")
    jedis.hmset("test", map1)

    val map2 = new util.HashMap[String, String]
    map2.put("k2-1", "v2-1")
    map2.put("k2-2", "v2-2")
    if (jedis.exists("test")) jedis.del("test")
    jedis.hmset("test", map2)
    println("====")

    val a = new java.util.ArrayList[Int]
    a.add(0)
    a.add(10)
    a.add(2)
    a.add(9)
    import scala.collection.JavaConversions._

    Collections.shuffle(a)


  }

//  @Test
  def test2 = {
    val jedis = new Jedis("10.201.48.13")
    if (jedis.exists("rcmd_gwyl_16639852578214573405287")) println("exist!")

  }

  @Test
  def test81 = {
    val jedis = new Jedis("10.201.129.74", 6378)
    Console println jedis.exists("rcmd_orig_h5_86907")
    for (i <- 0 until 1000)
    jedis.set("test" + i, "foo" + i)
    jedis.close()

  }

}
