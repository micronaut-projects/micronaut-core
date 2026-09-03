package io.micronaut.docs.factories.primitive

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EngineSpec:

  @Test
  def testEngine(): Unit =
    val context = ApplicationContext.run()
    try
      val engine = context.getBean(classOf[V8Engine])
      assertEquals(8, engine.getCylinders())
    finally context.close()
