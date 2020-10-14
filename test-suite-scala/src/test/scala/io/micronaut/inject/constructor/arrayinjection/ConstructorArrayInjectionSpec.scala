package io.micronaut.inject.constructor.arrayinjection

import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConstructorArrayInjectionSpec extends AbstractCompilerTest {

  @Test
  def `test array injection with constructor` ():Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[B])

    assertThat(b.a).isNotNull
    assertThat(b.a.length).isEqualTo(2)
    assertThat(b.a.contains(context.getBean(classOf[AImpl]))).isTrue
    assertThat(b.a.contains(context.getBean(classOf[AnotherImpl]))).isTrue
  }

  @Test
  def `test array injection with constructor - parsing`(): Unit = {
    val beanDefinition = buildBeanDefinition("test.$B","""
    |package test
    |
    |import io.micronaut.context.annotation._
    |import javax.inject._;
    |
    |@Inject
    |class B(val all:Array[A])
    |
    |@Singleton
    |class A
    |""".stripMargin)

    assertThat(beanDefinition).isNotNull
    assertThat(beanDefinition.getConstructor.getArguments.length).isEqualTo(1)
  }
}
