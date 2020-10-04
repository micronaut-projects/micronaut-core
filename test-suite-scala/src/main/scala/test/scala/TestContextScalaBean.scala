package test.scala

import io.micronaut.context.annotation.Context

@Context
class TestContextScalaBean {
    def getNotInjected() = "not injected"
}
