package io.micronaut.inject.visitor.beans.builder;

import io.micronaut.core.annotation.Introspected;

@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = SubBuilder.Builder.class))
public class SubBuilder extends SuperBuilder {
    private final String bar;

    private SubBuilder(String foo, String bar) {
        super(foo);
        this.bar = bar;
    }

    public String getBar() {
        return bar;
    }

    @Override
    public String toString() {
        return "SubBuilder{" +
            "foo='" + foo + '\'' +
            ", bar='" + bar + '\'' +
            '}';
    }

    public static class Builder extends SuperBuilder.Builder {
        private String bar;

        public Builder bar(String bar) {
            this.bar = bar;
            return this;
        }

        @Override
        public SubBuilder build() {
            return new SubBuilder(foo, bar);
        }
    }
}
