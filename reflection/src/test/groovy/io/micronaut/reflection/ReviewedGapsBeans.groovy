package io.micronaut.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.reflection.other.OtherBase
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * A type extending a super class of another package: only what that super class makes public is a property.
 */
@Introspected
class PackageSub extends OtherBase {
}

/**
 * An interface declaring an executable default method a bean inherits without overriding it.
 */
interface Greeting {

    @Executable
    default String greet() {
        return "hi"
    }
}

@Singleton
class GreeterBean implements Greeting {
}

@Singleton
class Counter {
    int calls
}

/**
 * A private injected method at two levels: neither overrides the other, and both are injection points.
 */
class InjectBase {

    @Inject
    private void wire(Counter counter) {
        counter.calls++
    }
}

@Singleton
class InjectSub extends InjectBase {

    @Inject
    private void wire(Counter counter) {
        counter.calls++
    }
}
