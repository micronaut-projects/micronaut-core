package io.micronaut.inject.aliasfor

import io.micronaut.inject.AbstractCompilerTest
import javax.inject.{Named, Qualifier}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AliasForQualifierSpec extends AbstractCompilerTest {
  @Test
  def testOther(): Unit = {
    val code =
      """
        |package test
        |
        |
        |import io.micronaut.inject.aliasfor._
        |import io.micronaut.inject.annotation._
        |import io.micronaut.context.annotation._
        |
        |@Factory
        |class Test {
        |    @TestAnnotation("foo") def myFunc():_root_.java.util.function.Function[String, Integer] = (str) => 10
        |}""".stripMargin

    val definition = buildBeanDefinition("test.$Test$MyFunc0", code)

    assertThat(definition).isNotNull
    assertThat(definition.getAnnotationTypeByStereotype(classOf[Qualifier])).isPresent
    assertThat(definition.getAnnotationTypeByStereotype(classOf[Qualifier]).get()).isEqualTo(classOf[TestAnnotation])
    assertThat(definition.getValue(classOf[Named], classOf[String]).get()).isEqualTo("foo")
  }
}
