package io.micronaut.inject.constructor.interfaceinjection

import io.micronaut.context.DefaultBeanContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConstructorWithInterfaceSpec {

  @Test
  def `test injection with constructor with an interface`(): Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[B])

    assertThat(b).isNotNull
    assertThat(b.a.isInstanceOf[AImpl]).isTrue
  }
}
