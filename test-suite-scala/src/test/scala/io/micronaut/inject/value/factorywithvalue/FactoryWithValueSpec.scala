package io.micronaut.inject.value.factorywithvalue

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.inject.AbstractCompilerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

class A(var port: Int)

class B(var a: A, var port: Int)

class FactoryWithValueSpec extends AbstractCompilerTest {

  @Test
  def `test factory with value compile`(): Unit = {
    val beanDefinition = buildBeanDefinition("io.micronaut.inject.value.factorywithvalue.$MyBean", """
    |package io.micronaut.inject.value.factorywithvalue
    |
    |import io.micronaut.context.annotation.Bean
    |import io.micronaut.context.annotation.Factory
    |import io.micronaut.context.annotation.Value;
    |import io.micronaut.context.annotation.Bean;
    |import io.micronaut.context.annotation.Factory;
    |import io.micronaut.context.annotation.Value;
    |
    |@Factory
    |class MyBean {
    |  @Bean
    |  def newA(@Value("${foo.bar}") port:Int):A = new A(port)
    |
    |  @Bean
    |  def newB(a:A, @Value("${foo.bar}") port:Int):B = new B(a, port)
    |
    |}""".stripMargin)

    assertThat( beanDefinition.hasAnnotation(classOf[Factory]))
  }

  def `test configuration injection with @Value`(): Unit = {
    val context = ApplicationContext.run(
      Map[String, AnyRef]("foo.bar" -> "8080").asJava
    )

    val a = context.getBean(classOf[A])
    val b = context.getBean(classOf[B])

    assertThat(a.port).isEqualTo(8080)
    assertThat(b.a).isNotNull
    assertThat(b.port).isEqualTo(8080)
  }
}
