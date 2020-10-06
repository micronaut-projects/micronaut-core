package test.scala

import javax.annotation.PostConstruct

@javax.inject.Singleton
class TestSingletonScalaBean() {
  var postConstructInvoked = false

  def getNotInjected() = "not injected - scala"

  @PostConstruct
  def postConstruct(): Unit = {
    postConstructInvoked = true;
  }
}


