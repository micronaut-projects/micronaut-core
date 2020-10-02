package io.micronaut.inject.visitor.beans

import io.micronaut.context.{ApplicationContext, DefaultApplicationContext}
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.jupiter.api.Test

class SingletonInjectValueConstructorBeanScalaTest {
  @Test def testApplicationContextJavaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.java.TestSingletonInjectValueConstructorBean])

    assertNotNull(singletonBean)
    assertEquals("injected String", singletonBean.getHost)
    assertEquals(42, singletonBean.getPort)
  }

  @Test def testApplicationContextScalaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.scala.TestSingletonInjectValueConstructorBean])

    assertNotNull(singletonBean)
    assertEquals("injected String", singletonBean.getHost)
    //assertEquals(42, singletonBean.getPort)
  }
}
