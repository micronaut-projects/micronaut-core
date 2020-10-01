package io.micronaut.inject.visitor.beans

import io.micronaut.context.DefaultApplicationContext
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.Test

class SingleBeanScalaTest {

  @Test
  def testApplicationContextScalaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext()
    applicationContext.start();

    val singletonBean = applicationContext.getBean(classOf[test.scala.TestSingletonBean])

    assertNotNull(singletonBean)
  }

  @Test
  def testApplicationContextJavaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext()
    applicationContext.start();

    val singletonBean = applicationContext.getBean(classOf[test.java.TestSingletonBean])

    assertNotNull(singletonBean)
  }
}
