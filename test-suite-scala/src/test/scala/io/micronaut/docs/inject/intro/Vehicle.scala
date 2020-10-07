package io.micronaut.docs.inject.intro

import javax.inject.Singleton

@Singleton class Vehicle(val engine: Engine) // <3>
{
  def start: String = engine.start
}
