package io.micronaut.inject.constructor.factoryinjection

import javax.inject.{Inject, Provider}

class AProvider(val c:C) extends Provider[A] {
  @Inject val another = null

  override def get = new AImpl(c, another)
}
