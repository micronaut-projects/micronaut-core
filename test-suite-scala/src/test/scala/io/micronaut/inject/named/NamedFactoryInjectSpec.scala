package io.micronaut.inject.named

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import javax.inject.{Inject, Singleton, Named}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

trait NamedFunction extends Function[String, String] // <- Scala function

@Singleton
class NamedFunctionBean(
  @Named("INPUT")  val inputFromConstructor: NamedFunction,
  @Named("OUTPUT") val outputFromConstructor: NamedFunction) {
  @Inject
  @Named("INPUT") private var privateFieldInput:NamedFunction = null
  @Inject
  @Named("OUTPUT") private var privateFieldOutput:NamedFunction = null

  def getInputFromConstructor: NamedFunction = inputFromConstructor

  def getOutputFromConstructor: NamedFunction = outputFromConstructor

  def getPrivateFieldInput: NamedFunction = privateFieldInput

  def getPrivateFieldOutput: NamedFunction = privateFieldOutput
}

@Factory
class NamedFunctionFactory {
  @Named("INPUT")
  @Singleton private[named] def inputFunction:NamedFunction = (s: String) => "INPUT " + s

  @Named("OUTPUT")
  @Singleton private[named] def outputFunction:NamedFunction = (s: String) => "OUTPUT " + s
}

class NamedFactoryInjectSpec {

  @Test
  def `test named factory inject`():Unit = {
      val context = ApplicationContext.run()

      val bean = context.getBean(classOf[NamedFunctionBean])

      assertThat(bean.getInputFromConstructor.apply("test")).isEqualTo("INPUT test")
      assertThat(bean.getOutputFromConstructor.apply("test")).isEqualTo("OUTPUT test")
      assertThat(bean.getPrivateFieldInput.apply("test")).isEqualTo("INPUT test")
      assertThat(bean.getPrivateFieldOutput.apply("test")).isEqualTo("OUTPUT test")
  }
}
