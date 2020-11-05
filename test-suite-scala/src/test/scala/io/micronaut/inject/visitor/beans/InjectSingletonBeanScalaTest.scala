package io.micronaut.inject.visitor.beans

import io.micronaut.context.DefaultApplicationContext
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertNotSame, assertSame, assertTrue}
import org.junit.jupiter.api.Test
import test.java.TestInjectSingletonBean

class InjectSingletonBeanScalaTest {
  @Test
  def testApplicationContextJavaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[TestInjectSingletonBean])
    assertNotNull(singletonBean)
    assertEquals("not injected", singletonBean.singletonBean.getNotInjected)
    //assertEquals("not injected", singletonBean.singletonScalaBean.getNotInjected())
    assertEquals(2, singletonBean.engines.length)
    assertTrue(singletonBean.v8Engine.isInstanceOf[test.java.V8Engine])
    assertTrue(singletonBean.v6Engine.isInstanceOf[test.java.V6Engine])
  }

  @Test
  def testApplicationContextScalaBean(): Unit = {
    val applicationContext = new DefaultApplicationContext
    applicationContext.start
    val singletonBean = applicationContext.getBean(classOf[test.scala.TestInjectSingletonScalaBean])
    assertNotNull(singletonBean)
    assertNotNull(singletonBean.singletonBean)
    assertEquals("not injected", singletonBean.singletonBean.getNotInjected)

    assertNotNull(singletonBean.singletonScalaBean)
    assertEquals("not injected - scala", singletonBean.singletonScalaBean.getNotInjected())
    assertEquals(2, singletonBean.engines.length)

    assertTrue(singletonBean.v8Engine.isInstanceOf[test.scala.V8Engine])
    assertTrue(singletonBean.v6Engine.isInstanceOf[test.scala.V6Engine])
    assertTrue(singletonBean.namedV6Engine.isInstanceOf[test.scala.V6Engine])
    assertTrue(singletonBean.namedV8Engine.isInstanceOf[test.scala.V8Engine])
    // Prototypes should be different
    assertNotSame(singletonBean.namedV6Engine, singletonBean.v6Engine);
    assertNotSame(singletonBean.namedV8Engine, singletonBean.v8Engine);

    assertNotNull(singletonBean.injectedFieldSingletonBean)
    assertNotNull(singletonBean.injectedFieldSingletonScalaBean)

    // Singletons are same
    assertSame(singletonBean.injectedFieldSingletonBean, singletonBean.singletonBean)
    assertSame(singletonBean.injectedFieldSingletonScalaBean, singletonBean.singletonScalaBean)

    assertTrue(singletonBean.existingOptionalEngine.isPresent)
    assertFalse(singletonBean.nonExistingOptionalEngine.isPresent)
  }
}
