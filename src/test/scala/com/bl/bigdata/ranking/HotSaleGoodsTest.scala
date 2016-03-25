package com.bl.bigdata.ranking

import org.junit._

/**
 * Created by MK33 on 2016/3/23.
 */
@Test
class HotSaleGoodsTest {
  val test = HotSaleGoods

  @Test
  def testGetDateBeforeNow = {
    assert(test.getDateBeforeNow(0) == "2016-03-24")
    assert(test.getDateBeforeNow(7) == "2016-03-17")
    println("===============")
    println(test.getDateBeforeNow(200))
    println("================")
  }

  @Test
  def testFilterDate = {
    assert(test.filterDate(("", "", 0, "2016-03-03 09:39:11.0", "", ""), "2016-03-01") == true)
    assert(test.filterDate(("", "", 0, "2016-03-01 00:00:00.0", "", ""), "2016-03-01") == true)
    assert(test.filterDate(("", "", 0, "2016-03-01 09:39:11.0", "", ""), "2016-03-02") == false)
  }

}