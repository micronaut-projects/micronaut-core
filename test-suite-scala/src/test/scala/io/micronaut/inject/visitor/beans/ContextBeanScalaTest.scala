package io.micronaut.inject.visitor.beans

import io.micronaut.context.{ApplicationContext, DefaultApplicationContext}
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.Test

class ContextBeanScalaTest {
  @Test def testApplicationContextScalaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.scala.TestContextBean])
    assertNotNull(singletonBean)
  }

  @Test def testApplicationContextJavaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.java.TestContextBean])
    assertNotNull(singletonBean)
  }
}
