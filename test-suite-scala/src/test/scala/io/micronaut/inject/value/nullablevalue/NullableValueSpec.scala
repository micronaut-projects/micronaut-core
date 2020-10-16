package io.micronaut.inject.value.nullablevalue

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

@Singleton
class A(
  @Value("${doesnt.exist}") @Nullable val nullConstructorArg: String,
  @Value("${exists.x}") val nonNullConstructorArg: String) {
  var nullMethodArg: String = null
  var nonNullMethodArg: String = null

  @Value("${doesnt.exist}") @Nullable var nullField: String = null

  @Value("${exists.x}") var nonNullField: String = null

  @Inject def injectedMethod(
    @Value("${doesnt.exist}") @Nullable nullMethodArg: String,
    @Value("${exists.x}") nonNullMethodArg: String): Unit = {
    this.nullMethodArg = nullMethodArg
    this.nonNullMethodArg = nonNullMethodArg
  }
}

class NullableValueSpec {

  @Test
  def `test value with nullable`():Unit = {
    val context = ApplicationContext.run(
      Map[String, AnyRef]("exists.x" -> "fromConfig").asJava,
      "test"
    )

    val a = context.getBean(classOf[A])

    assertThat(a.nullField).isNull()
    assertThat(a.nonNullField).isEqualTo("fromConfig")
    assertThat(a.nullConstructorArg).isNull()
    assertThat(a.nonNullConstructorArg).isEqualTo("fromConfig")
    assertThat(a.nullMethodArg).isNull()
    assertThat(a.nonNullMethodArg).isEqualTo("fromConfig")

  }
}