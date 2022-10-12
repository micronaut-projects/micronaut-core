package io.micronaut.inject.visitor;

import io.micronaut.core.annotation.*

@Introspected(accessKind=[Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD])
class Test {
    public String one
    public boolean invoked = false
    public String getOne() {
        invoked = true
        one
    }
}
