package io.micronaut.scala.traits

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AbstractCompilerTest
import javax.inject.Inject
import javax.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


  @Singleton
  class SomeBean

  @Singleton
  class SomeOtherBean extends SomeBean

  trait InjectsSomeBean[T <: SomeBean] {
    private[traits] var someBeanInjected:SomeBean = _
    private[traits] var somethingInjected:T = _

    @Inject
    def injectSomeBean(someBean:SomeBean):Unit = { someBeanInjected = someBean }

    @Inject
    def injectSomeThing(something:T):Unit = { somethingInjected = something }
  }

  trait AlsoInjectsSomeBean[T <: SomeBean] {
    private[traits] var someOtherBeanInjected:SomeOtherBean = _
    private[traits] var somethingElseInjected:T = _

    @Inject
    def injectSomeOtherBean(someOtherBean:SomeOtherBean):Unit = { someOtherBeanInjected = someOtherBean }

    @Inject
    def injectSomeThingElse(something:T):Unit = { somethingElseInjected = something }
  }

  @Singleton
  class SomeClass extends InjectsSomeBean[SomeBean] with AlsoInjectsSomeBean[SomeOtherBean]

class TraitSpec extends AbstractCompilerTest {
  @Test
  def `test @Injects in trait including generics`():Unit = {
    val beanDefinition = buildBeanDefinition("testspec.$SomeClass", """
       |package testspec
       |
       |import javax.inject.Inject
       |import javax.inject.Singleton
       |
       |@Singleton
       |class SomeBean
       |
       |@Singleton
       |class SomeOtherBean
       |
       |trait InjectsSomeBean[T] {
       |
       |  @Inject
       |  def injectSomeBean(someBean:SomeBean):Unit = { }
       |
       |  @Inject
       |  def injectSomeThing(something:T):Unit = { }
       |}
       |
       |trait InjectsSomeOtherBean[T] {
       |
       |  @Inject
       |  def injectSomeOtherBean(someOtherBean:SomeOtherBean):Unit = { }
       |
       |  @Inject
       |  def injectSomeThingElse(something:T):Unit = { }
       |}
       |
       |@Singleton
       |class SomeClass extends InjectsSomeBean[SomeBean] with InjectsSomeOtherBean[SomeOtherBean]
       |""".stripMargin)

    assertThat(beanDefinition.getInjectedMethods.size()).isEqualTo(4)

    assertThat(beanDefinition.getInjectedMethods.stream).anyMatch(it =>
      it.getMethod.getName == "injectSomeBean"
    )
    assertThat(beanDefinition.getInjectedMethods.stream).anyMatch(it =>
      it.getMethod.getName == "injectSomeOtherBean"
    )
    assertThat(beanDefinition.getInjectedMethods.stream).anyMatch(it =>
      it.getMethod.getName == "injectSomeThing"
    )
    assertThat(beanDefinition.getInjectedMethods.stream).anyMatch(it =>
      it.getMethod.getName == "injectSomeThingElse"
    )
  }

 // @Test // Shouldn't this work? The generic injected is being type erased and resolved to "Object"
 // I can tweak the plugin to set the argument type to the resolved type, but then it fails at run time
  // with NoSuchMethodError. Couldn't we use metadata to get around the type erased parameter?
  def `test traits for real`():Unit = {
    val context = ApplicationContext.run

    val someClass = context.getBean(classOf[SomeClass])

    assertThat(someClass).isNotNull

    assertThat(someClass.someBeanInjected)
      .isSameAs(someClass.somethingInjected)
      .isInstanceOf(classOf[SomeBean])

    assertThat(someClass.someOtherBeanInjected)
      .isSameAs(someClass.somethingElseInjected)
      .isInstanceOf(classOf[SomeOtherBean])
  }

  @Test
  def `test PostConstructs in traits`():Unit = {
  val beanDefinition = buildBeanDefinition("testspec.$SomeClassPostConstruct", """
       |package testspec
       |
       |import javax.inject.Inject
       |import javax.inject.Singleton
       |import javax.annotation.PostConstruct
       |
       |trait PostConstruct1 {
       |  @PostConstruct
       |  def init1():Unit = { }
       |}
       |
       |trait PostConstruct2 { self:PostConstruct1 =>
       |  @PostConstruct
       |  def init2():Unit = { }
       |}
       |
       |@Singleton
       |class SomeClassPostConstruct extends PostConstruct1 with PostConstruct2
       |""".stripMargin)

    assertThat(beanDefinition.getPostConstructMethods.size()).isEqualTo(2)

    assertThat(beanDefinition.getPostConstructMethods.stream).anyMatch(it =>
      it.getMethod.getName == "init1"
    )
    assertThat(beanDefinition.getPostConstructMethods.stream).anyMatch(it =>
      it.getMethod.getName == "init2"
    )

  }
}
