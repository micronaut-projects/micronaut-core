package io.micronaut.docs.ioc.visitor;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.version.annotation.Version;

@Version("1.0")
@Introspected
public class VersionedIntrospected {

    @Version("1.0")
    private String foo;

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

    @Version("1.0")
    public String getOther() {
        return foo;
    }
}
