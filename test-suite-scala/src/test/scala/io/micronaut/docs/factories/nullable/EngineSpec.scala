package io.micronaut.docs.factories.nullable

import java.util

import io.micronaut.context.ApplicationContext
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test

class EngineSpec {
  @Test
  def testEngineNull(): Unit = {
    val configuration: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]()
    configuration.put("engines.subaru.cylinders", Integer.valueOf(4))
    configuration.put("engines.ford.cylinders", Integer.valueOf(8))
    configuration.put("engines.ford.enabled", java.lang.Boolean.FALSE)
    configuration.put("engines.lamborghini.cylinders", Integer.valueOf(12))
    val applicationContext: ApplicationContext = ApplicationContext.run(configuration)
    val engines: util.Collection[Engine] = applicationContext.getBeansOfType(classOf[Engine])
    assertEquals("There are 2 engines", 2, engines.size)
    val totalCylinders: Int = engines.stream.mapToInt(_.getCylinders).sum
    assertEquals("Subaru + Lamborghini equals 16 cylinders", 16, totalCylinders)
  }
}
