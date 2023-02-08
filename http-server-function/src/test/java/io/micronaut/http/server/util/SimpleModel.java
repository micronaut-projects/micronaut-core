package io.micronaut.http.server.util;

import java.util.Objects;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class SimpleModel {
    private String foo;

    public SimpleModel(final String foo) {
        this.foo = foo;
    }

    public String getFoo() {
        return foo;
    }

    public void setFoo(final String foo) {
        this.foo = foo;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleModel that = (SimpleModel) o;
        return Objects.equals(foo, that.foo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(foo);
    }
}
