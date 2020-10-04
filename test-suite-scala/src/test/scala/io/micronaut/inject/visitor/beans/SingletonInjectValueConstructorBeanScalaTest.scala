package io.micronaut.inject.visitor.beans

import io.micronaut.context.DefaultApplicationContext
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.junit.jupiter.api.Test

class SingletonInjectValueConstructorBeanScalaTest {
  @Test def testApplicationContextJavaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.java.TestSingletonInjectValueConstructorBean])

    assertEquals("injected String", singletonBean.injectedString)
    assertEquals(41, singletonBean.injectedByte)
    assertEquals(42, singletonBean.injectedShort)
    assertEquals(43, singletonBean.injectedInt)
    assertEquals(44, singletonBean.injectedLong)
    assertEquals(44.1f, singletonBean.injectedFloat, 0.00001)
    assertEquals(44.2f, singletonBean.injectedDouble, 0.00001)
    assertEquals('#', singletonBean.injectedChar)
    assertTrue(singletonBean.injectedBoolean)
    assertEquals(45, singletonBean.lookUpInteger)
    assertEquals(46, singletonBean.injectIntField)
  }

  @Test def testApplicationContextScalaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.scala.TestSingletonInjectValueConstructorBean])

    assertNotNull(singletonBean)
    assertEquals("injected String", singletonBean.injectedString)
    assertEquals(41, singletonBean.injectedByte)
    assertEquals(42, singletonBean.injectedShort)
    assertEquals(43, singletonBean.injectedInt)
    assertEquals(44, singletonBean.injectedLong)
    assertEquals(44.1f, singletonBean.injectedFloat, 0.00001)
    assertEquals(44.2f, singletonBean.injectedDouble, 0.00001)
    assertEquals('#', singletonBean.injectedChar)
    assertTrue(singletonBean.injectedBoolean)
    assertEquals(45, singletonBean.lookUpInteger)
    assertEquals(46, singletonBean.injectIntField)
  }
}
