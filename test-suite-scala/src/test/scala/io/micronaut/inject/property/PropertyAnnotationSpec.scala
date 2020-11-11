package io.micronaut.inject.property

import java.util

import io.micronaut.context.ApplicationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class PropertyAnnotationSpec {

  @Test
  def `test inject properties`():Unit = {
    val ctx = ApplicationContext.run(
      mutable.LinkedHashMap[String, AnyRef](
        "my.string" -> "foo",
        "my.int" -> Integer.valueOf(10),
        "my.map.one" -> "one",
        "my.map.one.two" -> "two",
        "my.multi-value-map.one[0]" -> "one",
        "my.multi-value-map.one[1]" -> "two",
        "my.multi-value-map.one[2]" -> "three",
        "my.multi-value-map.two[0]" -> "two",
        "my.multi-value-map.one" ->  util.Arrays.asList("one", "two", "three"),
        "my.multi-value-map.two" ->  util.Arrays.asList("two")
      ).asJava
    )

    val constructorInjectedBean = ctx.getBean(classOf[ConstructorPropertyInject])
    val methodInjectedBean = ctx.getBean(classOf[MethodPropertyInject])
    val fieldInjectedBean = ctx.getBean(classOf[FieldPropertyInject])

    assertThat(constructorInjectedBean.nullable).isNull()
    assertThat(constructorInjectedBean.integer).isEqualTo(10)
    assertThat(constructorInjectedBean.str).isEqualTo("foo")
    assertThat(constructorInjectedBean.values.asScala.toArray).
      containsExactlyInAnyOrder("one" -> "one", "one.two" -> "two")

    assertThat(methodInjectedBean.getNullable).isNull()
    assertThat(methodInjectedBean.getInteger).isEqualTo(10)
    assertThat(methodInjectedBean.getStr).isEqualTo("foo")
    assertThat(methodInjectedBean.getValues.asScala.toArray).
      containsExactlyInAnyOrder("one" -> "one", "one.two" -> "two")

    assertThat(fieldInjectedBean.nullable).isNull()
    assertThat(fieldInjectedBean.integer).isEqualTo(10)
    assertThat(fieldInjectedBean.str).isEqualTo("foo")
    assertThat(fieldInjectedBean.values.asScala.toArray).
      containsExactlyInAnyOrder("one" -> "one", "one.two" -> "two")
  }

  @Test
  def `test a class with only a property annotation is a bean and injected`():Unit = {
    val ctx = ApplicationContext.run(
      mutable.HashMap[String, AnyRef]("my.int" -> Integer.valueOf(10)).asJava
    )
    assertThat(ctx.getBean(classOf[PropertyOnlyInject]).integer).isEqualTo(10)
  }
}
