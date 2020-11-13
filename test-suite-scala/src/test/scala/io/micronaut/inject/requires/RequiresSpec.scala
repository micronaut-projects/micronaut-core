package io.micronaut.inject.requires

import io.micronaut.context.{ApplicationContext, DefaultBeanContext}
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._


object Outer {

  class Inner {}

}

class RequiresSpec extends AbstractCompilerTest {

  @Test
  def `test requires property not equals`():Unit = {
    val beanDefinition = buildBeanDefinition("test.$MyBean", """
    |package test
    |
    |import io.micronaut.context.annotation._
    |
    |@Requires(property="foo", notEquals="bar")
    |@javax.inject.Singleton
    |class MyBean
    |""".stripMargin)

    var applicationContext = ApplicationContext.builder().build()
    applicationContext.getEnvironment.addPropertySource(PropertySource.of(Map[String, AnyRef]("foo" -> "test").asJava))
    applicationContext.getEnvironment.start()

    assertThat(beanDefinition.isEnabled(applicationContext)).isTrue

    applicationContext.close()
    applicationContext = ApplicationContext.builder().build()
    applicationContext.getEnvironment.addPropertySource(PropertySource.of(Map[String, AnyRef]("foo" -> "bar").asJava))
    applicationContext.getEnvironment.start()

    assertThat(beanDefinition.isEnabled(applicationContext)).isFalse

    applicationContext.close()
    applicationContext = ApplicationContext.builder().build()
    applicationContext.getEnvironment.start()

    assertThat(beanDefinition.isEnabled(applicationContext)).isTrue

    applicationContext.close()
  }

  @Test
  def `test requires classes with classes present`():Unit = {
    val beanDefinition = buildBeanDefinition("test.$MyBean", """
    |package test;
    |
    |import io.micronaut.context.annotation._
    |
    |@Requires(classes=Array(classOf[_root_.java.lang.String]))
    |@javax.inject.Singleton
    |class MyBean
    """.stripMargin)

    assertThat(beanDefinition.isEnabled(new DefaultBeanContext())).isTrue
  }

  @Test
  def `test meta requires condition not satisfied`():Unit = {
    val beanDefinition = buildBeanDefinition("test.$MyBean", """
    |package test
    |
    |import io.micronaut.context.annotation._
    |import io.micronaut.inject.requires._
    |
    |@MetaRequires
    |@javax.inject.Singleton
    |class MyBean
    """.stripMargin)

    assertThat(beanDefinition.isEnabled(new DefaultBeanContext())).isFalse
  }
}
