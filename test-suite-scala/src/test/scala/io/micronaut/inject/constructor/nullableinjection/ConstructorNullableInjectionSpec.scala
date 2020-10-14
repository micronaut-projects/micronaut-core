package io.micronaut.inject.constructor.nullableinjection

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertThrows
import org.junit.jupiter.api.Test

class ConstructorNullableInjectionSpec {

  @Test
  def `test nullable injection with constructor`(): Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[B])

    assertThat(b.a).isNull
  }

  @Test
  def `test normal injection still fails`(): Unit = {
    val context = new DefaultBeanContext()
    context.start()

    assertThrows(classOf[DependencyInjectionException], () => {
      val c = context.getBean(classOf[C])
    })
  }
}
