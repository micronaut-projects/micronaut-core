package io.micronaut.inject.constructor.multipleinjection

import io.micronaut.context.DefaultBeanContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConstructorMultipleInjectionSpec {

  @Test
  def `test injection with constructor`(): Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[B])

    assertThat(b.a).isNotNull
    assertThat(b.a).isSameAs(context.getBean(classOf[A]))
    assertThat(b.c).isNotNull
    assertThat(b.c).isSameAs(context.getBean(classOf[C]))
  }
}
