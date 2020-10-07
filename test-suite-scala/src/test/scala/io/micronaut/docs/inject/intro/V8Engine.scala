package io.micronaut.docs.inject.intro

import javax.inject.Singleton

@Singleton class V8Engine extends Engine {

  override def start: String =  "Starting V8"

  override def getCylinders: Int = cylinders

  def setCylinders (cylinders: Int) {
    this.cylinders = cylinders
  }

  private var cylinders: Int = 8
}
