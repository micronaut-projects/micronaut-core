package io.micronaut.inject.visitor.beans

import io.micronaut.context.DefaultApplicationContext
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.junit.jupiter.api.Test

class SingletonBeanScalaTest {

  @Test
  def testApplicationContextScalaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext()
    applicationContext.start();

    val singletonBean = applicationContext.getBean(classOf[test.scala.TestSingletonScalaBean])

    assertNotNull(singletonBean)
    assertEquals("not injected - scala", singletonBean.getNotInjected())
    assertTrue(singletonBean.postConstructInvoked)
  }

  @Test
  def testApplicationContextJavaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext()
    applicationContext.start();

    val singletonBean = applicationContext.getBean(classOf[test.java.TestSingletonBean])

    assertNotNull(singletonBean)
    assertEquals("not injected", singletonBean.getNotInjected)
    assertTrue(singletonBean.postConstructInvoked)
  }
}
