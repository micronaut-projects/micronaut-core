package io.micronaut.inject.qualifiers.primary

import java.util

import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Primary
import io.micronaut.context.exceptions.NonUniqueBeanException
import org.junit.jupiter.api.Test
import javax.inject.{Inject, Singleton}
import org.assertj.core.api.Assertions.{assertThat, assertThatExceptionOfType}

trait A

@Singleton class A1 extends A

@Primary
@Singleton class A2 extends A

class B {
  @Inject var all: util.List[A] = null
  @Inject var a: A = null
}

trait C

@Primary
@Singleton class C1 extends C

@Primary
@Singleton class C2 extends C

class PrimarySpec {

  @Test
  def `test the @Primary annotation influences bean selection`():Unit = {
    val context = BeanContext.run()

    val b = context.getBean(classOf[B])

    assertThat(b.all).hasSize(2)
    assertThat(b.all.stream().anyMatch(_.isInstanceOf[A1]))
    assertThat(b.all.stream().anyMatch(_.isInstanceOf[A2]))
    assertThat(b.a).isInstanceOf(classOf[A2])
  }

  @Test
  def `test duplicate @Primary bean throws exception`() {
    val context = BeanContext.run()

    assertThatExceptionOfType(classOf[NonUniqueBeanException])
      .isThrownBy(() => context.getBean(classOf[C]))
  }
}
