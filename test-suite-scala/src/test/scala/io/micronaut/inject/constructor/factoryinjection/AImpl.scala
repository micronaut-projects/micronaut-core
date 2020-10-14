package io.micronaut.inject.constructor.factoryinjection

import javax.inject.{Inject, Singleton}

@Singleton
class AImpl(val c:C, val c2:C) extends A {
  @Inject private[factoryinjection] var d: D = _
}