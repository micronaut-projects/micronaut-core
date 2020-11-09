package io.micronaut.inject.qualifiers.replaces

import java.util

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Replaces
import javax.inject.{Inject, Named, Singleton}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

trait A

@Singleton
class A1 extends A

@Replaces(classOf[A1])
@Singleton
class A2 extends A

class B {
  @Inject private var all: util.List[A] = _
  @Inject private var a: A = _

  def getAll: util.List[A] = all

  def setAll(all: util.List[A]): Unit = {
    this.all = all
  }

  def getA: A = a

  def setA(a: A): Unit = {
    this.a = a
  }
}

trait E

@Singleton
@Named("E1") class E1 extends E

@Singleton
@Replaces(bean = classOf[E], named = "E1")
class E1Replacement extends E

@Singleton
@Named("E2") class E2 extends E


class ReplacesSpec {
  @Test
  def `test that a bean can be marked to replace another bean`():Unit = {
    val context = new DefaultBeanContext().start()

    val b = context.getBean(classOf[B])

    assertThat(b.getAll).hasSize(1)
    assertThat(b.getAll.stream.anyMatch(_.isInstanceOf[A1])).isFalse
    assertThat(b.getAll.stream.anyMatch(_.isInstanceOf[A2])).isTrue
    assertThat(b.getA.isInstanceOf[A2]).isTrue
  }

  @Test
  def `test that named beans can be replaced`():Unit = {
    val context = new DefaultBeanContext().start()

    assertThat(context.containsBean(classOf[E1Replacement])).isTrue
    assertThat(context.containsBean(classOf[E2])).isTrue
    assertThat(context.containsBean(classOf[E])).isTrue
    assertThat(context.getBeansOfType(classOf[E])).hasSize(2)
    assertThat(context.getBeansOfType(classOf[E])).contains(context.getBean(classOf[E1Replacement]))
    assertThat(context.getBeansOfType(classOf[E])).contains(context.getBean(classOf[E2]))
  }
}
