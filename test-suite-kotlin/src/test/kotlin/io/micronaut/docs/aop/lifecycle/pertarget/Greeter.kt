package io.micronaut.docs.aop.lifecycle.pertarget

import io.micronaut.runtime.context.scope.Refreshable

@Refreshable
@Audited
open class Greeter {

    open fun greet(name: String) = "Hello $name"

    open fun farewell(name: String) = "Goodbye $name"
}
