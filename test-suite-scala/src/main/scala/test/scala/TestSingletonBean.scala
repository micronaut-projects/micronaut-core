package test.scala

import io.micronaut.context.annotation.Value

@javax.inject.Singleton
class TestSingletonBean() {
  def getHost() = "not injected"
}


