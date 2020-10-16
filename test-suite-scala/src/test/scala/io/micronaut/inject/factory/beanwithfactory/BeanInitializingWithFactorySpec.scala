package io.micronaut.inject.factory.beanwithfactory

import io.micronaut.context.DefaultBeanContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BeanInitializingWithFactorySpec {

  @Test
  def `test bean initializing event listener`():Unit = {
      val context = new DefaultBeanContext().start()

      val b = context.getBean(classOf[B])

    // FIXME      assertThat(b.name).isEqualTo("CHANGED")

      val listener = context.getBean(classOf[DualListener])

    // FIXME       assertThat(listener.initialized).isTrue
    // FIXME     assertThat(listener.created).isTrue
  }
}
