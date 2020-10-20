package io.micronaut.inject.generics

import io.micronaut.context.ApplicationContext
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
    |class ChainB[A] extends ChainC[A, Number, Int]
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
    assertThat(definition.getTypeArguments("test.ChainC").get(1).getType).isSameAs(classOf[Number])
    assertThat(definition.getTypeArguments("test.ChainC").get(2).getType).isSameAs(classOf[Int])
    assertThat(definition.getTypeArguments("test.ChainD")).size().isEqualTo(4)
    assertThat(definition.getTypeArguments("test.ChainD").get(0).getType).isSameAs(classOf[Boolean])
    assertThat(definition.getTypeArguments("test.ChainD").get(1).getType).isSameAs(classOf[Number])
    assertThat(definition.getTypeArguments("test.ChainD").get(2).getType).isSameAs(classOf[String])
    assertThat(definition.getTypeArguments("test.ChainD").get(3).getType).isSameAs(classOf[Int])
    assertThat(definition.getTypeArguments("test.ChainE")).size().isEqualTo(4)
    assertThat(definition.getTypeArguments("test.ChainE").get(0).getType).isSameAs(classOf[Boolean])
    assertThat(definition.getTypeArguments("test.ChainE").get(1).getType).isSameAs(classOf[Number])
    assertThat(definition.getTypeArguments("test.ChainE").get(2).getType).isSameAs(classOf[String])
    assertThat(definition.getTypeArguments("test.ChainE").get(3).getType).isSameAs(classOf[Byte])
  }

  @Test
  def `test type arguments with Java classes mixed in`(): Unit = {
    val context = ApplicationContext.run()
    val beanDefinition = context.getBeanDefinition(classOf[ScalaClassA]);

    assertThat(beanDefinition.getTypeArguments("io.micronaut.inject.generics.ScalaClassB")).size().isEqualTo(1)
    assertThat(beanDefinition.getTypeArguments("io.micronaut.inject.generics.ScalaClassB").get(0).getType).isSameAs(classOf[String])
    assertThat(beanDefinition.getTypeArguments("io.micronaut.inject.generics.JavaClassC")).size().isEqualTo(2)
    assertThat(beanDefinition.getTypeArguments("io.micronaut.inject.generics.JavaClassC").get(0).getType).isSameAs(classOf[Array[Char]])
    assertThat(beanDefinition.getTypeArguments("io.micronaut.inject.generics.JavaClassC").get(1).getType).isSameAs(classOf[String])
    assertThat(beanDefinition.getTypeArguments("io.micronaut.inject.generics.JavaInterfaceD")).size().isEqualTo(3)
    assertThat(beanDefinition.getTypeArguments("io.micronaut.inject.generics.JavaInterfaceD").get(0).getType).isSameAs(classOf[Array[Int]])
    assertThat(beanDefinition.getTypeArguments("io.micronaut.inject.generics.JavaInterfaceD").get(1).getType).isSameAs(classOf[Array[Char]])
    assertThat(beanDefinition.getTypeArguments("io.micronaut.inject.generics.JavaInterfaceD").get(2).getType).isSameAs(classOf[String])
  }

  @Test
  def `test type array type arguments`(): Unit = {
    val definition = buildBeanDefinition("test.$ArrayChain",
      """
        |package test;
        |
        |import javax.inject.Singleton;
        |
        |abstract class AbstractArrayChain[
        | BOOLEAN,
        | INT,
        | FLOAT,
        | DOUBLE,
        | LONG,
        | BYTE,
        | SHORT,
        | CHAR,
        | STRING,
        | INTARRAY,
        | STRINGARRAY
        |]
        |
        |@Singleton
        |class ArrayChain extends AbstractArrayChain[
        | Array[Boolean],
        | Array[Int],
        | Array[Float],
        | Array[Double],
        | Array[Long],
        | Array[Byte],
        | Array[Short],
        | Array[Char],
        | Array[String],
        | Array[Array[Int]],
        | Array[Array[String]]
        |]
        |
    """.stripMargin)

    assertThat(definition.getTypeArguments("test.AbstractArrayChain")).size().isEqualTo(11)
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(0).getType).isSameAs(classOf[Array[Boolean]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(1).getType).isSameAs(classOf[Array[Int]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(2).getType).isSameAs(classOf[Array[Float]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(3).getType).isSameAs(classOf[Array[Double]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(4).getType).isSameAs(classOf[Array[Long]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(5).getType).isSameAs(classOf[Array[Byte]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(6).getType).isSameAs(classOf[Array[Short]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(7).getType).isSameAs(classOf[Array[Char]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(8).getType).isSameAs(classOf[Array[String]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(9).getType).isSameAs(classOf[Array[Array[Int]]])
    assertThat(definition.getTypeArguments("test.AbstractArrayChain").get(10).getType).isSameAs(classOf[Array[Array[String]]])
  }

  @Test
  def `test type scala List type arguments`(): Unit = {
    val definition = buildBeanDefinition("test.$ListChain",
      """
        |package test;
        |
        |import javax.inject.Singleton;
        |
        |abstract class AbstractListChain[L]
        |
        |@Singleton
        |class ListChain extends AbstractListChain[
        | List[Int]
        |]
    """.stripMargin)

    assertThat(definition.getTypeArguments("test.AbstractListChain")).size().isEqualTo(1)
    assertThat(definition.getTypeArguments("test.AbstractListChain").get(0).getType).isSameAs(Nil.getClass)
  }
}
