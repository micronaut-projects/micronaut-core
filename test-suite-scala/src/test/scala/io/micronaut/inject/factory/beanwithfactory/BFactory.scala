package io.micronaut.inject.factory.beanwithfactory

import io.micronaut.context.annotation.{Factory, Prototype}
import javax.annotation.PostConstruct
import javax.inject.Inject

@Factory
class BFactory {
  private[beanwithfactory] var name = "original"
  private[beanwithfactory] var postConstructCalled = false
  private[beanwithfactory] var getCalled = false

  @Inject private val fieldA:A = null
  @Inject protected var anotherField: A = null
  @Inject private[beanwithfactory] val a:A = null

  private var methodInjected:A = null

  @Inject private def injectMe(a: A) = {
    methodInjected = a
    methodInjected
  }

  private[beanwithfactory] def getFieldA = fieldA

  private[beanwithfactory] def getAnotherField = anotherField

  private[beanwithfactory] def getMethodInjected = methodInjected

  @PostConstruct private[beanwithfactory] def init(): Unit = {
    postConstructCalled = true
    name = name.toUpperCase
  }

  @Prototype def get: B = {
    getCalled = true
    val b = new B
    b.name = name
    b
  }
}
