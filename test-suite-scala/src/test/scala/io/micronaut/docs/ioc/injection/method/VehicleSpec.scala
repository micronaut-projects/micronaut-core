package io.micronaut.docs.ioc.injection.method

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Test

class VehicleSpec:

  @Test
  def methodInjectionInjectsEngine(): Unit =
    val context = ApplicationContext.run()
    try
      context.getBean(classOf[Vehicle]).start()
    finally context.close()
