package io.micronaut.docs.qualifiers.annotation

import javax.inject.Singleton

@Singleton class V6Engine extends Engine { // <2>
  private val cylinders = 6

  override def getCylinders: Int = cylinders

  override def start = "Starting V6"
}