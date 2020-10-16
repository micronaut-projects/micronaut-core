package io.micronaut.inject.value.singletonwithvalue

import java.net.URL
import java.util.Optional

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import javax.inject.{Inject, Singleton}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

@Singleton
class A(@Value("${foo.bar}") var fromConstructor: Int) {
  @Value("${camelCase.URL}") var url:URL = null
  @Value("${foo.bar}") var optionalPort:Optional[Int] = null
  @Value("${foo.another}") var optionalPort2: Optional[Int]= null
  @Value("${foo.bar}") var port = 0
  private var anotherPort = 0
  @Value("${foo.bar}") protected var fieldPort = 0
  @Value("${default.port:9090}") protected var defaultPort = 0

  @Inject def setAnotherPort(@Value("${foo.bar}") port: Int): Unit = {
    anotherPort = port
  }

   def getAnotherPort = anotherPort

   def getFieldPort = fieldPort

   def getDefaultPort = defaultPort

  def getFromConstructor: Int = fromConstructor

  def setFromConstructor(fromConstructor: Int): Unit = {
    this.fromConstructor = fromConstructor
  }

  def getOptionalPort: Optional[Int] = optionalPort

  def setOptionalPort(optionalPort: Optional[Int]): Unit = {
    this.optionalPort = optionalPort
  }

  def getOptionalPort2: Optional[Int] = optionalPort2

  def setOptionalPort2(optionalPort2: Optional[Int]): Unit = {
    this.optionalPort2 = optionalPort2
  }

  def getPort: Int = port

  def setPort(port: Int): Unit = {
    this.port = port
  }

  def setFieldPort(fieldPort: Int): Unit = {
    this.fieldPort = fieldPort
  }

  def setDefaultPort(defaultPort: Int): Unit = {
    this.defaultPort = defaultPort
  }
}

@Singleton class B(var a: A, @Value("${foo.bar}") var fromConstructor: Int) {
  def getFromConstructor: Int = fromConstructor

  def setFromConstructor(fromConstructor: Int): Unit = {
    this.fromConstructor = fromConstructor
  }

  def getA: A = a

  def setA(a: A): Unit = {
    this.a = a
  }
}

class ValueSpec {

  @Test
  def `test configuration injection with @Value`(): Unit = {
    val context = ApplicationContext.run(Map[String,AnyRef](
      "foo.bar" -> "8080",
      "camelCase.URL" -> "http://localhost"
    ).asJava)

    val a = context.getBean(classOf[A])
    val b = context.getBean(classOf[B])

    assertThat(a.url).isEqualTo(new URL("http://localhost"))
    assertThat(a.port).isEqualTo(8080)
    assertThat(a.optionalPort.get()).isEqualTo(8080)
    assertThat(a.optionalPort2).isEmpty()
    assertThat(a.getFieldPort).isEqualTo(8080)
    assertThat(a.getAnotherPort).isEqualTo(8080)
    assertThat(a.getDefaultPort).isEqualTo(9090)
    assertThat(b.fromConstructor).isEqualTo(8080)
    assertThat(b.a).isNotNull()
  }
}
