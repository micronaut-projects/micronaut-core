package io.micronaut.docs.ioc.injection.ctor

import io.micronaut.core.annotation.Creator
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Vehicle @Inject(val engine: Engine): // <1>

  def this() =
    this(Engine.create(6))

  def start(): Unit =
    engine.start()

@Singleton
class Engine @Creator(): // <2>
  private var cylinderCount = 8

  private def this(cylinders: Int) =
    this()
    cylinderCount = cylinders

  def cylinders: Int = cylinderCount

  def start(): Unit =
    println(s"Vrooom! $cylinders")

object Engine:

  def create(cylinders: Int): Engine =
    new Engine(cylinders)
