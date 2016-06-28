package com.bl.bigdata.mail

import java.text.SimpleDateFormat
import java.util.Date

import com.bl.bigdata.util.ConfigurationBL
import org.apache.commons.mail.{DefaultAuthenticator, SimpleEmail}
import org.apache.logging.log4j.LogManager

/**
  * Created by MK33 on 2016/3/28.
  */
object MailServer {
  private val logger = LogManager.getLogger(this.getClass.getName)

  def send(message: String) = {
    val email = getEmail(ConfigurationBL.get("mail.type"))
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    email.setMsg(message)
    email.setSubject("redis report: " + sdf.format(new Date))
    logger.info(s"send email: $message")
    email.send
  }

  def send(msg: Array[String]): Unit = {
    for (m <- msg) {
      send(m)
      Thread.sleep(1000)
    }
  }

  def getEmail(mailType: String): SimpleEmail = {
    val email = new SimpleEmail
    if (mailType.equals("sina")) {
      email.setHostName("smtp.sina.com")
      email.setAuthenticator(new DefaultAuthenticator("disanyuzhou2016@sina.com", "sh1@bl2$3"))
      email.setFrom("disanyuzhou2016@sina.com")
      for (who <- ConfigurationBL.get("mail.to").split(",")) email.addTo(who)
      email
    } else {
      email.setHostName("mail.bl.com")
      email.setAuthenticator(new DefaultAuthenticator("MK33", "Make819307659"))
      email.setFrom("Ke.Ma@bl.com")
      for (who <- ConfigurationBL.get("mail.to").split(",")) email.addTo(who)
      email
    }
  }

  def main(args: Array[String]) {
    ConfigurationBL.init()
    val email = getEmail("tt")
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    email.setMsg("test")
    email.setSubject("redis report: " + sdf.format(new Date))
    logger.info(s"send email:")
    email.send
  }
}
