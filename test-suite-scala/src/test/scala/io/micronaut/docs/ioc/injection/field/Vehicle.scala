package io.micronaut.docs.ioc.injection.field

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Vehicle:
  @Inject var engine: Engine = null // <1>

  def start(): Unit =
    engine.start()

@Singleton
class Engine:
  def start(): Unit =
    println("Vrooom!")
