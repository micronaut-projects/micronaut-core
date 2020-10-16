package io.micronaut.inject.method.arrayinjection

import java.util

import io.micronaut.context.DefaultBeanContext
import javax.inject.{Inject, Singleton}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

trait A

@Singleton class AImpl extends A

@Singleton class AnotherImpl extends A

@Singleton class B {
  private var all: Array[A] = null

  @Inject def setA(a: Array[A]): Unit = {
    this.all = a
  }

  def getAll: Array[A] = this.all
}

class SetterArrayInjectionSpec {

  @Test
  def `test injection via setter that takes an array`(): Unit = {
    val context = new DefaultBeanContext().start()

    val b = context.getBean(classOf[B])

    assertThat(b.getAll).isNotNull
    assertThat(b.getAll.size).isEqualTo(2)
    assertThat(b.getAll.contains(context.getBean(classOf[AImpl])))
    assertThat(b.getAll.contains(context.getBean(classOf[AnotherImpl])))
  }
}
