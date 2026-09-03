package io.micronaut.docs.ioc.injection.nullable

import jakarta.inject.Singleton

@Singleton
class Vehicle(injectedEngine: Engine | Null): // <1>
  val engine: Engine = if injectedEngine != null then injectedEngine else Engine.create(6) // <2>

  def start(): Unit =
    engine.start()

case class Engine(cylinders: Int):

  def start(): Unit =
    println(s"Vrooom! $cylinders")

object Engine:

  def create(cylinders: Int): Engine =
    Engine(cylinders)
