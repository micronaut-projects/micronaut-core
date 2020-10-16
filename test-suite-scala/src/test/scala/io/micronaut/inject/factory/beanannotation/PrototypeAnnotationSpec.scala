package io.micronaut.inject.factory.beanannotation

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Prototype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Prototype class A

class PrototypeAnnotationSpec {
  @Test
  def `test @bean annotation makes a class available as a bean`():Unit = {
    val beanContext = new DefaultBeanContext().start()

    assertThat(beanContext.getBean(classOf[A])).isNotSameAs(beanContext.getBean(classOf[A]))
  }
}
