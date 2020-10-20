package io.micronaut.inject.generics

import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeArgumentsSpec extends AbstractCompilerTest {

  @Test
  def `test type arguments are passed through to the parent`(): Unit = {
    val definition = buildBeanDefinition("test.$ChainA","""
    |package test;
    |
    |import javax.inject.Singleton;
    |
    |@Singleton
    |class ChainA extends ChainB[Boolean]
    |
    |class ChainB[A] extends ChainC[A, Array[Int], Int]
    |
    |abstract class ChainC[A, B, E] extends ChainD[A, B, String, E]
    |
    |trait ChainD[A, B, C, E] extends ChainE[A, B, C, Byte]
    |
    |trait ChainE[A, B, C, D]
    """.stripMargin)

    assertThat(definition.getTypeArguments("test.ChainB")).size().isEqualTo(1)
    assertThat(definition.getTypeArguments("test.ChainB").get(0).getType).isSameAs(classOf[Boolean])
    assertThat(definition.getTypeArguments("test.ChainC").size()).isEqualTo(3)
    assertThat(definition.getTypeArguments("test.ChainC").get(0).getType).isSameAs(classOf[Boolean])
    assertThat(definition.getTypeArguments("test.ChainC").get(1).getType).isSameAs(classOf[Array[Int]])
    assertThat(definition.getTypeArguments("test.ChainC").get(2).getType).isSameAs(classOf[Int])
    assertThat(definition.getTypeArguments("test.ChainD")).size().isEqualTo(4)
    assertThat(definition.getTypeArguments("test.ChainD").get(0).getType).isSameAs(classOf[Boolean])
    assertThat(definition.getTypeArguments("test.ChainD").get(1).getType).isSameAs(classOf[Array[Int]])
    assertThat(definition.getTypeArguments("test.ChainD").get(2).getType).isSameAs(classOf[String])
    assertThat(definition.getTypeArguments("test.ChainD").get(3).getType).isSameAs(classOf[Int])
    assertThat(definition.getTypeArguments("test.ChainE")).size().isEqualTo(4)
    assertThat(definition.getTypeArguments("test.ChainE").get(0).getType).isSameAs(classOf[Boolean])
    assertThat(definition.getTypeArguments("test.ChainE").get(1).getType).isSameAs(classOf[Array[Int]])
    assertThat(definition.getTypeArguments("test.ChainE").get(2).getType).isSameAs(classOf[String])
    assertThat(definition.getTypeArguments("test.ChainE").get(3).getType).isSameAs(classOf[Byte])
  }
}
