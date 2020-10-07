package io.micronaut.docs.factories

import javax.inject.Singleton

@Singleton
class Vehicle(engine: Engine) {

  def start() = engine.start()

}
