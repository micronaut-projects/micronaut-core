package io.micronaut.inject.visitor.beans

import io.micronaut.context.{ApplicationContext, DefaultApplicationContext}
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.jupiter.api.Test

class InjectConstructorSingletonBeanScalaTest {
  @Test def testApplicationContextJavaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.java.TestInjectConstructorSingletonBean])
    assertNotNull(singletonBean)
    assertEquals("not injected", singletonBean.singletonBean.getNotInjected)
  }

  @Test def testApplicationContextScalaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.scala.TestInjectConstructorSingletonBean])
    assertNotNull(singletonBean)
    assertEquals("not injected", singletonBean.singletonBean.getNotInjected())
  }
}
