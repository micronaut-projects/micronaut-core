package io.micronaut.inject.constructor.optionalinjection

import io.micronaut.context.DefaultBeanContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{Disabled, Test}

class ConstructorOptionalSpec {

  @Test
  def `test injection of optional objects`(): Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[B])

    assertThat(b.a).isPresent
    assertThat(b.c).isEmpty

  }

  /* TODO
  It looks like to make this work the AbstractBeanDefinition.getBeanForConstructorArgument method
  would need a Scala version to test for Scala Option
 */
  @Test
  @Disabled
  def `test injection of scala option objects`(): Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[BScala])

    assertThat(b.a.isDefined).isTrue
    assertThat(b.c.isEmpty).isTrue
  }
}
