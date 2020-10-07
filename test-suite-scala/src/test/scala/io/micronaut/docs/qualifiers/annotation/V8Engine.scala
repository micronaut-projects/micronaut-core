package io.micronaut.docs.qualifiers.annotation

import javax.inject.Singleton

@Singleton class V8Engine extends Engine {
  private val cylinders = 8

  override def getCylinders: Int = cylinders

  override def start = "Starting V8"
}
