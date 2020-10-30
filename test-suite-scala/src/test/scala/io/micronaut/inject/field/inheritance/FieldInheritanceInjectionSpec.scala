package io.micronaut.inject.field.inheritance

import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.AbstractCompilerTest
import javax.inject.{Inject, Singleton}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


trait AbstractListenerTrait[T <: SomeAbstractBean]  {
  @Inject var someBeanTrait:SomeBean = _

  @Inject var genericTrait:T = _
}

abstract class AbstractListenerClass[T <: SomeAbstractBean] {
  @Inject var someBeanAbstractClass:SomeBean = _

  @Inject var genericAbstractClass:T = _
}

abstract class SomeAbstractBean

@Singleton
class SomeBean extends SomeAbstractBean

@Singleton
class Listener extends AbstractListenerClass[SomeBean] with AbstractListenerTrait[SomeBean] {
  @Inject var someBeanClass:SomeBean = _
}

class FieldInheritanceInjectionSpec extends AbstractCompilerTest {

  @Test
  def `test injecting into super abstract class`():Unit = {
    val bean = buildBeanDefinition("test.$Listener", """
     |package test;
     |
     |import io.micronaut.inject.AbstractCompilerTest
     |import javax.inject.Singleton
     |import javax.inject.Inject
     |import _root_.scala.annotation.meta.setter
     |
     |trait AbstractListenerTrait[T <: SomeAbstractBean]  {
     |  @Inject var someBeanTrait:SomeBean = _
     |
     |  @Inject var genericTrait:T = _
     |}
     |
     |abstract class AbstractListenerClass[T <: SomeAbstractBean] {
     |  @Inject var someBeanAbstractClass:SomeBean = _
     |
     |  @Inject var genericAbstractClass:T = _
     |}
     |
     |abstract class SomeAbstractBean
     |
     |@Singleton
     |class SomeBean extends SomeAbstractBean
     |
     |@Singleton
     |class Listener extends AbstractListenerClass[SomeBean] with AbstractListenerTrait[SomeBean] {
     |  @Inject var someBeanClass:SomeBean = _
     |}
     |""".stripMargin)

    assertThat(bean).isNotNull
    assertThat(bean.getInjectedFields).hasSize(5)

    assertThat(bean.getInjectedFields.stream).anyMatch(it =>
      it.getField.getName == "someBeanClass"
    )
    assertThat(bean.getInjectedFields.stream).anyMatch(it =>
      it.getField.getName == "genericAbstractClass"
    )
    assertThat(bean.getInjectedFields.stream).anyMatch(it =>
      it.getField.getName == "someBeanAbstractClass"
    )
    assertThat(bean.getInjectedFields.stream).anyMatch(it =>
      it.getField.getName == "genericTrait"
    )
    assertThat(bean.getInjectedFields.stream).anyMatch(it =>
      it.getField.getName == "someBeanTrait"
    )

    assertThat(bean.getInjectedMethods).hasSize(0)
  }

  @Test
  def `test with real classes`():Unit = {
    val context = new DefaultBeanContext().start()

    val b = context.getBean(classOf[Listener])

    assertThat(b.someBeanClass)
      .isInstanceOf(classOf[SomeBean])
      .isSameAs(b.someBeanAbstractClass)
      .isSameAs(b.someBeanTrait)
      .isSameAs(b.genericAbstractClass)
      .isSameAs(b.genericTrait)
  }
}
