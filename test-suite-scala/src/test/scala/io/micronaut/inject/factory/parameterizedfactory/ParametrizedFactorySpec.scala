package io.micronaut.inject.factory.parameterizedfactory

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.{Factory, Parameter, Prototype}
import javax.annotation.{Nullable, PostConstruct}
import javax.inject.{Inject, Singleton}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._


@Singleton class A {
  private[parameterizedfactory] var name = "A"

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }
}

class B {
  private[parameterizedfactory] var name:String = null

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }
}

@Factory
class BFactory {
  private[parameterizedfactory] var name = "fromFactory"
  private[parameterizedfactory] var postConstructCalled = false
  private[parameterizedfactory] var getCalled = false
  @Inject private var fieldA:A = null
  @Inject protected var anotherField: A = null
  @Inject private[parameterizedfactory] var a:A = null
  private var methodInjected:A = null

  @Inject private def injectMe(a: A): Unit = {
    methodInjected = a
  }

  private[parameterizedfactory] def getFieldA = fieldA

  private[parameterizedfactory] def getAnotherField = anotherField

  private[parameterizedfactory] def getMethodInjected = methodInjected

  @PostConstruct private[parameterizedfactory] def init(): Unit = {
    assertState()
    postConstructCalled = true
    name = name.toUpperCase
  }

  @Singleton private[parameterizedfactory] def get = {
    assert(postConstructCalled, "post construct should have been called")
    assertState()
    getCalled = true
    val b = new B
    b.setName(name)
    b
  }

  @Prototype private[parameterizedfactory] def buildC(b: B, @Parameter count: Int) = new C(b, count)

  private def assertState(): Unit = {
    assert(fieldA != null, "private fields should have been injected first")
    assert(anotherField != null, "protected fields should have been injected field")
    assert(a != null, "public properties should have been injected first")
    assert(methodInjected != null, "methods should have been injected first")
  }
}

class C(var b: B, var count: Int)

@Inject
@Prototype class D (@Nullable @Parameter val someBean: A)

class ParametrizedFactorySpec {

  @Test
  def `test parametrized factory definition`():Unit = {
    val beanContext = new DefaultBeanContext().start()


    val c = beanContext.createBean(classOf[C],
      Map[String, AnyRef]("count" -> Integer.valueOf(10)).asJava)
    assertThat(c).isNotNull
    assertThat(c.count).isEqualTo(10)
    assertThat(c.b).isNotNull
  }
}
