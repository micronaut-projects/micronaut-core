package io.micronaut.docs.qualifiers.annotation

import javax.inject.Singleton

@Singleton class Vehicle(@V8 val engine: Engine) // <3>
{
  def start: String = engine.start
}
