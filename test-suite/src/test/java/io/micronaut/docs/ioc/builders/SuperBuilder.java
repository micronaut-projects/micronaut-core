package io.micronaut.docs.ioc.builders;

import io.micronaut.core.annotation.Introspected;

@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = SuperBuilder.Builder.class))
public class SuperBuilder {
    protected final String foo;

    SuperBuilder(String foo) {
        this.foo = foo;
    }

    public String getFoo() {
        return foo;
    }

    public static class Builder {
        protected String foo;

        Builder() {
        }

        public Builder foo(String foo) {
            this.foo = foo;
            return this;
        }

        public SuperBuilder build() {
            return new SuperBuilder(foo);
        }
    }
}
