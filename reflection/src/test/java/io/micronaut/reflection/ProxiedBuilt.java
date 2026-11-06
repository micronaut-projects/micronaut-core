package io.micronaut.reflection;

import io.micronaut.core.annotation.Introspected;

/**
 * An interface naming a static builder method of its own, which a {@link java.lang.reflect.Proxy} of it does not
 * declare and does not build.
 */
@Introspected(builder = @Introspected.IntrospectionBuilder(builderMethod = "builder"))
public interface ProxiedBuilt {

    String getName();

    static Builder builder() {
        return new Builder();
    }

    /**
     * The builder the interface names.
     */
    final class Builder {

        private String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public ProxiedBuilt build() {
            String built = name;
            return () -> built;
        }
    }
}
