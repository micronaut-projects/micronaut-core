package io.micronaut.inject.qualifiers.secondary

import java.util

import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Secondary
import io.micronaut.inject.qualifiers.Qualifiers
import javax.inject.{Inject, Singleton}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

trait A

@Secondary
@Singleton
class A1 extends A

@Singleton
class A2 extends A

class B {
  @Inject private var all: util.List[A] = null
  @Inject private var a: A = null

  def getAll: util.List[A] = all

  def setAll(all: util.List[A]): Unit = {
    this.all = all
  }

  def getA: A = a

  def setA(a: A): Unit = {
    this.a = a
  }
}

class SecondarySpec {

  @Test
  def `test the @Secondary annotation influences bean selection`(): Unit = {
    val context = BeanContext.run()

     val b = context.getBean(classOf[B])

    assertThat(b.getAll.size).isEqualTo(2)
    assertThat(b.getAll.stream().anyMatch( _.isInstanceOf[A1])).isTrue
    assertThat(b.getAll.stream().anyMatch( _.isInstanceOf[A2])).isTrue
    assertThat(b.getA.isInstanceOf[A2]).isTrue
    assertThat(context.getBean(classOf[A], Qualifiers.byStereotype(classOf[Secondary])).isInstanceOf[A1]).isTrue
  }
}
