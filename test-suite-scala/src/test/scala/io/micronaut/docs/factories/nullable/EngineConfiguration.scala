package io.micronaut.docs.factories.nullable

import io.micronaut.context.annotation.{EachProperty, Property}
import io.micronaut.core.util.Toggleable

// tag::class[]
@EachProperty("engines")
class EngineConfiguration extends Toggleable {
  private var enabled = true
  private var cylinders:Integer = _

  def getCylinders: Integer = cylinders

  def setCylinders(cylinders: Integer): Unit = {
    this.cylinders = cylinders
  }

  override def isEnabled: Boolean = enabled

  def setEnabled(enabled: Boolean): Unit = {
    this.enabled = enabled
  }
}
// end::class[]

