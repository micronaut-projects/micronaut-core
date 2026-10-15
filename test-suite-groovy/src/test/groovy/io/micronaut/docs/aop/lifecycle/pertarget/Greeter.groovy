package io.micronaut.docs.aop.lifecycle.pertarget

import io.micronaut.runtime.context.scope.Refreshable

@Refreshable
@Audited
class Greeter {

    String greet(String name) {
        "Hello $name"
    }

    String farewell(String name) {
        "Goodbye $name"
    }
}
