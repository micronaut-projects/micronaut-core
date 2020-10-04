package test.scala

import io.micronaut.context.annotation.Context

@Context
class TestContextBean {
    def getNotInjected() = "not injected"
}
