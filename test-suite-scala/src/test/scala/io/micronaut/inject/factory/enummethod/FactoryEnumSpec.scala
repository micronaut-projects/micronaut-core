package io.micronaut.inject.factory.enummethod

import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FactoryEnumSpec extends AbstractCompilerTest {

  @Test
  def `test a factory can return an enum`():Unit = {
    val context = buildContext("test.$TestFactory", """
    |package test
    |
    |import io.micronaut.inject.annotation._
    |import io.micronaut.context.annotation._
    |import io.micronaut.inject.factory.enummethod.TestEnum
    |
    |@Factory
    |class TestFactory {
    |
    |  @javax.inject.Singleton
    |  def testEnum():TestEnum = TestEnum.ONE
    |}
    """.stripMargin)

    assertThat(context.containsBean(classOf[TestEnum])).isTrue
    assertThat(context.getBean(classOf[TestEnum]) == TestEnum.ONE).isTrue
  }

}
