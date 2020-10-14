package io.micronaut.inject.constructor.collectioninjection

import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{Disabled, Test}

class ConstructorCollectionInjectionSpec extends AbstractCompilerTest {

  @Test
  def `test collection injection with constructor` ():Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[B])

    assertThat(b.a).isNotNull
    assertThat(b.a.size()).isEqualTo(2)
    assertThat(b.a.contains(context.getBean(classOf[AImpl]))).isTrue
    assertThat(b.a.contains(context.getBean(classOf[AnotherImpl]))).isTrue
  }

  /* TODO
 It looks like to make this work the AbstractBeanDefinition.getBeanForConstructorArgument method
 would need a Scala version to test for Scala collections
*/
  @Test
  @Disabled
  def `test scala collection injection with constructor` ():Unit = {
    val context = new DefaultBeanContext()
    context.start()

    val b = context.getBean(classOf[BScala])

    assertThat(b.a).isNotNull
    assertThat(b.a.length).isEqualTo(2)
    assertThat(b.a.contains(context.getBean(classOf[AImpl]))).isTrue
    assertThat(b.a.contains(context.getBean(classOf[AnotherImpl]))).isTrue
  }

  @Test
  def `test collection injection with constructor - parsing`(): Unit = {
    val beanDefinition = buildBeanDefinition("test.$B","""
      |package test
      |
      |import _root_.java.util.Collection
      |import io.micronaut.context.annotation._
      |import javax.inject._;
      |
      |@Inject
      |class B(val all:Collection[A])
      |
      |@Singleton
      |class A
      |""".stripMargin)

    assertThat(beanDefinition).isNotNull
    assertThat(beanDefinition.getConstructor.getArguments.length).isEqualTo(1)
  }
}
