package test.scala

import io.micronaut.context.annotation.Value

@javax.inject.Singleton
class TestSingletonInjectValueConstructorBean(
  @Value(value = "injected String") val host:String,
  //@Value("42") val port:Int
) {
  def getHost() = host

  //def getPort() = port
}
