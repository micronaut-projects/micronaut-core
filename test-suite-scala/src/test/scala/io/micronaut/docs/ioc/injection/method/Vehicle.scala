package io.micronaut.docs.ioc.injection.method

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Vehicle:
  private var engine: Engine = null

  @Inject // <1>
  def initialize(engine: Engine): Unit =
    this.engine = engine

  def start(): Unit =
    engine.start()

@Singleton
class Engine:
  def start(): Unit =
    println("Vrooom!")
