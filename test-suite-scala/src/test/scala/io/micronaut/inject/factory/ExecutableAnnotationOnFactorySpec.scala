package io.micronaut.inject.factory

import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExecutableAnnotationOnFactorySpec extends AbstractCompilerTest {
  @Test
  def `test executable annotation on factory`():Unit = {
      val definition = buildBeanDefinition("test.$Test$MyFunc0","""
    |package test;
    |
    |import io.micronaut.inject.annotation._
    |import io.micronaut.context.annotation._
    |
    |@Factory
    |class Test {
    |
    |  @Bean
    |  @Executable
    |  def myFunc():_root_.java.util.function.Function[String, Int] = (str) => 10;
    |}
    |""".stripMargin)

        assertThat(definition).isNotNull
        assertThat(definition.findMethod("apply", classOf[String])).isPresent
        assertThat(definition.getTypeArguments(classOf[java.util.function.Function[_,_]]).size()).isEqualTo(2)
        assertThat(definition.getTypeArguments(classOf[java.util.function.Function[_,_]]).get(0).getName)
          .isEqualTo("T")
        assertThat(definition.getTypeArguments(classOf[java.util.function.Function[_,_]]).get(1).getName)
          .isEqualTo("R")
        assertThat(definition.getTypeArguments(classOf[java.util.function.Function[_,_]]).get(0).getType)
          .isEqualTo(classOf[String])
        assertThat(definition.getTypeArguments(classOf[java.util.function.Function[_,_]]).get(1).getType)
          .isEqualTo(classOf[Int])
  }
}
