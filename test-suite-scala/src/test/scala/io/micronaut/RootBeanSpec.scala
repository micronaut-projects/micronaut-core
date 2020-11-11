package io.micronaut

import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.{assertThat, assertThatExceptionOfType, catchThrowableOfType}
import org.junit.jupiter.api.Test

class RootBeanSpec extends AbstractCompilerTest {
  @Test
  def `test that abstract bean definitions are built for abstract classes`(): Unit = {
    assertThatExceptionOfType(classOf[RuntimeException])
      .isThrownBy(() => buildBeanDefinition("test.$RootBean",
        """|import io.micronaut.context.annotation._
           |
           |@javax.inject.Singleton
           |class RootBean
           |""".stripMargin))
      .withMessage("Micronaut beans cannot be in the default package")
    }
}
