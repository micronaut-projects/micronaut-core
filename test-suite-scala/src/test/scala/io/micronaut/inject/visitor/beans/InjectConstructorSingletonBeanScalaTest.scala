package io.micronaut.inject.visitor.beans

import io.micronaut.context.DefaultApplicationContext
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.junit.jupiter.api.Test

class InjectConstructorSingletonBeanScalaTest {
  @Test def testApplicationContextJavaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.java.TestInjectConstructorSingletonBean])
    assertNotNull(singletonBean)
    assertEquals("not injected", singletonBean.singletonBean.getNotInjected)
    //assertEquals("not injected", singletonBean.singletonScalaBean.getNotInjected())
    assertEquals(2, singletonBean.engines.length)
    assertTrue(singletonBean.v8Engine.isInstanceOf[test.java.V8Engine])
    assertTrue(singletonBean.v6Engine.isInstanceOf[test.java.V6Engine])
  }

  @Test def testApplicationContextScalaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.scala.TestInjectConstructorSingletonScalaBean])
    assertNotNull(singletonBean)
    assertEquals("not injected", singletonBean.singletonBean.getNotInjected)
    assertEquals("not injected - scala", singletonBean.singletonScalaBean.getNotInjected())
    assertEquals(2, singletonBean.engines.length)
    assertTrue(singletonBean.v8Engine.isInstanceOf[test.scala.V8Engine])
    assertTrue(singletonBean.v6Engine.isInstanceOf[test.scala.V6Engine])
  }
}
