package io.micronaut.inject.constructor.providerinjection

import io.micronaut.context.DefaultBeanContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConstructorWithProviderSpec {

  @Test
  def `test injection with constructor supplied by a provider`(): Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[B])

    assertThat(b.a).isNotNull
    assertThat(b.a.get().isInstanceOf[AImpl]).isTrue
    assertThat(b.a.get()).isSameAs(context.getBean(classOf[AImpl]))
    assertThat(b.a.get().asInstanceOf[AImpl].c).isNotNull
    assertThat(b.a.get().asInstanceOf[AImpl].c2).isNotNull
  }
}
