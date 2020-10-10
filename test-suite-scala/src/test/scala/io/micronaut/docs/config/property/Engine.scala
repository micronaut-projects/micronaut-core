package io.micronaut.docs.config.property

// tag::imports[]

import io.micronaut.context.annotation.Property
import javax.inject.Inject
import javax.inject.Singleton

// end::imports[]

// tag::class[]
@Singleton class Engine {
  @Property(name = "my.engine.cylinders")
  protected var cylinders: Int = 0

  private var manufacturer: String = _

  def getCylinders: Int = cylinders

  def getManufacturer: String = manufacturer

  @Inject def setManufacturer(@Property(name = "my.engine.manufacturer") manufacturer: String): Unit = {
    this.manufacturer = manufacturer
  }
}

// end::class[]