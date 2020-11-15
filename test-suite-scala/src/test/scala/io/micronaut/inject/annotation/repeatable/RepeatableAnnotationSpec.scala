package io.micronaut.inject.annotation.repeatable

import io.micronaut.context.annotation.{Property, Requirements, Requires}
import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RepeatableAnnotationSpec extends AbstractCompilerTest {

  @Test
  def `test repeatable annotation properties with alias`():Unit = {
      val definition = buildBeanDefinition("test.$Test","""
    |package test
    |
    |import io.micronaut.inject.annotation.repeatable._
    |import io.micronaut.context.annotation._
    |
    |@OneRequires(properties = Array(new Property(name = "prop1", value = "value1")))
    |@SomeOther(properties = Array(new Property(name = "prop2", value = "value2")))
    |@javax.inject.Singleton
    |class Test {
    |  @OneRequires(properties = Array(new Property(name = "prop2", value = "value2")))
    |   private[test] def someMethod(): Unit = {  }
    |}
    """.stripMargin)

    val someOther= definition.synthesize(classOf[SomeOther])
    val oneRequires = definition.synthesize(classOf[OneRequires])

    assertThat(oneRequires.properties()).hasSize(1)
    assertThat(oneRequires.properties()(0).name()).isEqualTo("prop1")
    assertThat(oneRequires.properties()(0).value()).isEqualTo("value1")
    assertThat(someOther.properties()).hasSize(1)
    assertThat(someOther.properties()(0).name()).isEqualTo("prop2")
    assertThat(someOther.properties()(0).value()).isEqualTo("value2")
  }

  @Test
  def `test repeatable annotation resolve all values with single @Requires`():Unit = {
    val definition = buildBeanDefinition("test.$Test","""
    |package test
    |
    |import io.micronaut.inject.annotation.repeatable._
    |import io.micronaut.context.annotation._
    |
    |@OneRequires
    |@Requires(property="bar")
    |@javax.inject.Singleton
    |class Test {
    |
    |}
    """.stripMargin)

    val  requirements = definition.getAnnotationValuesByType(classOf[Requires])
    val  requires = definition.synthesizeAnnotationsByType(classOf[Requires])

    assertThat(definition.getAnnotationMetadata().hasAnnotation(classOf[Requires])).isTrue
    assertThat(definition.getAnnotationMetadata().hasAnnotation(classOf[Requirements])).isTrue

    assertThat(requirements).isNotNull
    assertThat(requirements).hasSize(2)
    assertThat(requires).hasSize(2)
  }

  @Test
  def `test repeatable annotation resolve all values with single @Requires - reverse`():Unit = {
    val definition = buildBeanDefinition("test.$Test","""
    |package test
    |
    |import io.micronaut.inject.annotation.repeatable._;
    |import io.micronaut.context.annotation._
    |
    |@Requires(property="bar")
    |@OneRequires
    |@javax.inject.Singleton
    |class Test
    """.stripMargin)

    val  requirements = definition.getAnnotationValuesByType(classOf[Requires])
    val  requires = definition.synthesizeAnnotationsByType(classOf[Requires])

    assertThat(definition.getAnnotationMetadata().hasAnnotation(classOf[Requires])).isTrue
    assertThat(definition.getAnnotationMetadata().hasAnnotation(classOf[Requirements])).isTrue

    assertThat(requirements).isNotNull
    assertThat(requirements).hasSize(2)
    assertThat(requires).hasSize(2)
  }

  @Test
  def `test repeatable annotation resolve inherited from meta annotations`():Unit = {
    val definition = buildBeanDefinition("test.$Test","""
   |package test
   |
   |import io.micronaut.inject.annotation.repeatable._
   |import io.micronaut.context.annotation._
   |
   |@OneRequires
   |@TwoRequires
   |@javax.inject.Singleton
   |class Test
    """.stripMargin)
    val requirements = definition.getAnnotationValuesByType(classOf[Requires])
    val requires = definition.synthesizeAnnotationsByType(classOf[Requires])

    assertThat(definition.getAnnotationMetadata().hasStereotype(classOf[Requires])).isTrue
    assertThat(definition.getAnnotationMetadata().hasStereotype(classOf[Requirements])).isTrue
    assertThat(definition.getAnnotationMetadata().hasAnnotation(classOf[Requires])).isFalse
    assertThat(definition.getAnnotationMetadata().hasAnnotation(classOf[Requirements])).isFalse

    assertThat(requirements).isNotNull
    assertThat(requirements).hasSize(2)
    assertThat(requires).hasSize(2)
  }

  @Test
  def `test repeatable annotation resolve inherited from meta annotations - reverse`():Unit = {
    val definition = buildBeanDefinition("test.$Test","""
   |package test
   |
   |import io.micronaut.inject.annotation.repeatable._
   |import io.micronaut.context.annotation._
   |
   |@TwoRequires
   |@OneRequires
   |@javax.inject.Singleton
   |class Test
    """.stripMargin)
    val requirements = definition.getAnnotationValuesByType(classOf[Requires])
    val requires = definition.synthesizeAnnotationsByType(classOf[Requires])

    assertThat(definition.getAnnotationMetadata().hasStereotype(classOf[Requires])).isTrue
    assertThat(definition.getAnnotationMetadata().hasStereotype(classOf[Requirements])).isTrue
    assertThat(definition.getAnnotationMetadata().hasAnnotation(classOf[Requires])).isFalse
    assertThat(definition.getAnnotationMetadata().hasAnnotation(classOf[Requirements])).isFalse

    assertThat(requirements).isNotNull
    assertThat(requirements).hasSize(2)
    assertThat(requires).hasSize(2)
  }

  @Test
  def `test repeatable annotation resolve all values with multiple @Requires`():Unit = {
    val definition = buildBeanDefinition("test.$Test","""
    |package test
    |
    |import io.micronaut.inject.annotation.repeatable._
    |import io.micronaut.context.annotation._
    |
    |@OneRequires
    |@Requires(property="bar")
    |@Requires(property="another")
    |@javax.inject.Singleton
    |class Test
    """.stripMargin)

    val requirements = definition.getAnnotationValuesByType(classOf[Requires])
    val requires = definition.synthesizeAnnotationsByType(classOf[Requires])

    assertThat(definition.getAnnotationMetadata().hasStereotype(classOf[Requires])).isTrue
    assertThat(definition.getAnnotationMetadata().hasStereotype(classOf[Requirements])).isTrue
    assertThat(requirements).isNotNull
    assertThat(requirements).hasSize(3)
    assertThat(requires).hasSize(3)
  }

  @Test
  def `test repeatable annotation resolve all values with multiple @Requires - reverse`():Unit = {
    val definition = buildBeanDefinition("test.$Test","""
    |package test
    |
    |import io.micronaut.inject.annotation.repeatable._
    |import io.micronaut.context.annotation._
    |
    |@Requires(property="bar")
    |@Requires(property="another")
    |@OneRequires
    |@javax.inject.Singleton
    |class Test
    """.stripMargin)

    val requirements = definition.getAnnotationValuesByType(classOf[Requires])
    val requires = definition.synthesizeAnnotationsByType(classOf[Requires])

    assertThat(definition.getAnnotationMetadata().hasStereotype(classOf[Requires])).isTrue
    assertThat(definition.getAnnotationMetadata().hasStereotype(classOf[Requirements])).isTrue
    assertThat(requirements).isNotNull
    assertThat(requirements).hasSize(3)
    assertThat(requires).hasSize(3)
  }

  @Test
  def `test repeatable annotation resolve all values with multiple declared and inherited @Requires`():Unit = {
    val definition = buildBeanDefinition("test.$Test","""
    |package test
    |
    |import io.micronaut.inject.annotation.repeatable._
    |import io.micronaut.context.annotation._
    |
    |@TwoRequires
    |@Requires(property="bar")
    |@Requires(property="another")
    |@javax.inject.Singleton
    |class Test
    """.stripMargin)

    val requirements = definition.getAnnotationValuesByType(classOf[Requires])
    val requires = definition.synthesizeAnnotationsByType(classOf[Requires])

    assertThat(requirements).isNotNull
    assertThat(requirements).hasSize(4)
    assertThat(requires).hasSize(4)
  }

  @Test
  def `test repeatable annotation resolve all values with multiple inherited @Requires`():Unit = {
    val definition = buildBeanDefinition("test.$Test","""
    |package test
    |
    |import io.micronaut.inject.annotation.repeatable._
    |import io.micronaut.context.annotation._
    |
    |@TwoRequires
    |@Requires(property="bar")
    |@javax.inject.Singleton
    |class Test
    """.stripMargin)

    val requirements = definition.getAnnotationValuesByType(classOf[Requires])
    val requires = definition.synthesizeAnnotationsByType(classOf[Requires])

    assertThat(requirements).isNotNull
    assertThat(requirements).hasSize(3)
    assertThat(requires).hasSize(3)
  }

  @Test
  def `test repeatable annotation resolve all values with multiple inherited @Requires - reverse`():Unit = {
    val definition = buildBeanDefinition("test.$Test","""
    |package test
    |
    |import io.micronaut.inject.annotation.repeatable._
    |import io.micronaut.context.annotation._
    |
    |@Requires(property="bar")
    |@TwoRequires
    |@javax.inject.Singleton
    |class Test
    """.stripMargin)

    val requirements = definition.getAnnotationValuesByType(classOf[Requires])
    val requires = definition.synthesizeAnnotationsByType(classOf[Requires])

    assertThat(requirements).isNotNull
    assertThat(requirements).hasSize(3)
    assertThat(requires).hasSize(3)
  }
}
