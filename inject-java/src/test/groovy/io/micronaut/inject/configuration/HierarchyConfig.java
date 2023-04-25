package io.micronaut.inject.configuration;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("test.props")
public class HierarchyConfig {

    @ConfigurationBuilder(prefixes = "with")
    RealizedBuilder builder = new RealizedBuilder();

    public static class RealizedBuilder extends Builder<RealizedBuilder> {
    }

    public abstract static class Builder<T> {

        private String name;

        public String getName() {
            return name;
        }

        public final T withName(String name) {
            this.name = name;
            return getSubclass();
        }

        public final T withName(NameHolder name) {
            this.name = name.name;
            return getSubclass();
        }

        public NameHolder build() {
            return new NameHolder(name);
        }

        @SuppressWarnings("unchecked")
        protected final T getSubclass() {
            return (T) this;
        }
    }

    public static class NameHolder {

        private final String name;

        public NameHolder(String name) {
            this.name = name;
        }
    }
}
