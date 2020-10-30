package io.micronaut.inject.method.inheritance

import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.AbstractCompilerTest
import javax.inject.{Inject, Singleton}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

trait AbstractListenerTrait[T <: SomeAbstractBean]  {

  private[inheritance] var someBeanInjectedByMethodTrait:SomeBean = _

  private[inheritance] var tInjectedByMethodTrait:T = _

  @Inject def injectSomeBeanTrait(someBean:SomeBean):Unit = {
    someBeanInjectedByMethodTrait = someBean
  }

  @Inject def injectTTrait(t:T):Unit = {
    tInjectedByMethodTrait = t
  }
}

abstract class AbstractListenerClass[T <: SomeAbstractBean] {

  private[inheritance] var someBeanInjectedByMethodAbstractClass:SomeBean = _

  private[inheritance] var tInjectedByMethodAbstractClass:T = _

  @Inject def injectSomeBeanAbstractClass(someBean:SomeBean):Unit = {
    someBeanInjectedByMethodAbstractClass = someBean
  }

  @Inject def injectTAbstractClass(t:T):Unit = {
    tInjectedByMethodAbstractClass = t
  }
}

abstract class SomeAbstractBean

@Singleton
class SomeBean extends SomeAbstractBean

@Singleton
class Listener extends AbstractListenerClass[SomeBean] with AbstractListenerTrait[SomeBean] {
  private[inheritance] var someBeanInjectedByMethodClass:SomeBean = _

  @Inject def injectSomeBeanClass(someBean:SomeBean):Unit = {
    someBeanInjectedByMethodClass = someBean
  }
}

class MethodInheritanceInjectionSpec extends AbstractCompilerTest {

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
     |
     |  private var someBeanInjectedByMethodTrait:SomeBean = _
     |
     |  private var tInjectedByMethodTrait:T = _
     |
     |  @Inject def injectSomeBeanTrait(someBean:SomeBean):Unit = {
     |    someBeanInjectedByMethodTrait = someBean
     |  }
     |
     |  @Inject def injectTTrait(t:T):Unit = {
     |    tInjectedByMethodTrait = t
     |  }
     |}
     |
     |abstract class AbstractListenerClass[T <: SomeAbstractBean] {
     |
     |  private var someBeanInjectedByMethodAbstractClass:SomeBean = _
     |
     |  private var tInjectedByMethodAbstractClass:T = _
     |
     |  @Inject def injectSomeBeanAbstractClass(someBean:SomeBean):Unit = {
     |    someBeanInjectedByMethodAbstractClass = someBean
     |  }
     |
     |  @Inject def injectTAbstractClass(t:T):Unit = {
     |    tInjectedByMethodAbstractClass = t
     |  }
     |}
     |
     |abstract class SomeAbstractBean
     |
     |@Singleton
     |class SomeBean extends SomeAbstractBean
     |
     |@Singleton
     |class Listener extends AbstractListenerClass[SomeBean] with AbstractListenerTrait[SomeBean] {
     |private var someBeanInjectedByMethodClass:SomeBean = _
     |
     |@Inject def injectSomeBeanClass(someBean:SomeBean):Unit = {
     |  someBeanInjectedByMethodClass = someBean
     |}
     |
     |}
     |""".stripMargin)

    assertThat(bean).isNotNull
    assertThat(bean.getInjectedFields).hasSize(0)
    assertThat(bean.getInjectedMethods).hasSize(5)

    assertThat(bean.getInjectedMethods.stream).anyMatch(it =>
      it.getMethod.getName == "injectSomeBeanClass"
    )
    assertThat(bean.getInjectedMethods.stream).anyMatch(it =>
      it.getMethod.getName == "injectTAbstractClass"
    )
    assertThat(bean.getInjectedMethods.stream).anyMatch(it =>
      it.getMethod.getName == "injectSomeBeanAbstractClass"
    )
    assertThat(bean.getInjectedMethods.stream).anyMatch(it =>
      it.getMethod.getName == "injectTTrait"
    )
    assertThat(bean.getInjectedMethods.stream).anyMatch(it =>
      it.getMethod.getName == "injectSomeBeanTrait"
    )
  }

  @Test
  def `test with real classes`():Unit = {
    val context = new DefaultBeanContext().start()

    val b = context.getBean(classOf[Listener])

    assertThat(b.someBeanInjectedByMethodClass)
      .isInstanceOf(classOf[SomeBean])
      .isSameAs(b.someBeanInjectedByMethodAbstractClass)
      .isSameAs(b.someBeanInjectedByMethodTrait)
      .isSameAs(b.tInjectedByMethodAbstractClass)
      .isSameAs(b.tInjectedByMethodTrait)
  }
}
