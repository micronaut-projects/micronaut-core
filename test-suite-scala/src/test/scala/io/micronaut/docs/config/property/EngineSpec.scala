package io.micronaut.docs.config.property

import io.micronaut.context.ApplicationContext
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

class EngineSpec {
  @Test def testStartVehicleWithConfiguration(): Unit = {
    val map = new java.util.LinkedHashMap[String, AnyRef]()
    map.put("my.engine.cylinders", "8")
    map.put("my.engine.manufacturer", "Honda")
    val ctx: ApplicationContext = ApplicationContext.run(map)
    val engine: Engine = ctx.getBean(classOf[Engine])
    assertEquals("Honda", engine.getManufacturer)
    assertEquals(8, engine.getCylinders)
    ctx.close()
  }
}
