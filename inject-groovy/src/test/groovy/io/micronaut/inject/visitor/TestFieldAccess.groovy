package io.micronaut.inject.visitor


import groovy.transform.PackageScope
import io.micronaut.core.annotation.Introspected

@Introspected(accessKind=Introspected.AccessKind.FIELD)
class TestFieldAccess {
    public String one // read/write
    public final int two // read-only
    @PackageScope String three // package protected
    protected String four // protected can be read from the same package
    private String five // not included since private

    TestFieldAccess(int two) {
        this.two = two
    }
}
