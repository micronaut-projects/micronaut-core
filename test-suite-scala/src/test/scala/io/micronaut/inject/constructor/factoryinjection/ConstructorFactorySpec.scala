package io.micronaut.inject.constructor.factoryinjection

import io.micronaut.context.DefaultBeanContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConstructorFactorySpec {

  @Test
  def `test injection with constructor supplied by a provider`(): Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[B])

    assertThat(b.a).isNotNull
    assertThat(b.a.isInstanceOf[AImpl]).isTrue
    assertThat(b.a.c).isNotNull
    assertThat(b.a.c2).isNotNull
    assertThat(b.a.d).isNotNull
    assertThat(b.a).isSameAs(context.getBean(classOf[AImpl]))
  }
}
