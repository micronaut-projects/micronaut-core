package io.micronaut.inject.beans

import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AbstractBeanSpec extends AbstractCompilerTest {

  @Test
  def `test that abstract bean definitions are built for abstract classes`(): Unit = {
    val definition = buildBeanDefinition("test.$AbstractBean",
      """package test
        |
        |import io.micronaut.context.annotation._
        |
        |@javax.inject.Singleton
        |abstract class AbstractBean {
        |    @Value("server.host")
        |    var host:String = _
        |}
        |""".stripMargin)

      assertThat(definition).isNotNull
      assertThat(definition.isAbstract).isTrue
      assertThat(definition.getInjectedFields.size).isEqualTo(1)
  }
}
