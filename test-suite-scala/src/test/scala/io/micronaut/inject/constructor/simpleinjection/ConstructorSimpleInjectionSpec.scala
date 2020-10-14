package io.micronaut.inject.constructor.simpleinjection

import io.micronaut.context.{BeanContext, DefaultBeanContext}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConstructorSimpleInjectionSpec {

  @Test
  def `test injection with constructor`():Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b =  context.getBean(classOf[B])
    val b2 =  context.getBean(classOf[B2])

    assertThat(b.a).isNotNull
    assertThat(b2.a).isNotNull
    assertThat(b2.a2).isNotNull

    val bd = context.getBeanDefinition(classOf[B])
    val bd2 = context.getBeanDefinition(classOf[B2])

    assertThat(bd.getRequiredComponents.size).isEqualTo(1)
    assertThat(bd.getRequiredComponents.contains(classOf[A])).isTrue
    assertThat(bd2.getRequiredComponents.size).isEqualTo(1)
    assertThat(bd2.getRequiredComponents.contains(classOf[A])).isTrue
  }
}
