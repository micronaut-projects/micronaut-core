package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

@ConfigurationProperties("test.props")
public class A1ConfigProperties {
    @ConfigurationBuilder(prefixes = "set")
    Something builder = new Something();

    class Something {
        Set<Foo> foos = new HashSet<>();

        public Set<Foo> getFoos() {
            return foos;
        }

        public void setFoos(Set<Foo> foos) {
            this.foos = foos;
        }
    }

    public Something getBuilder() {
        builder.foos.add(Foo.AUTO_ADDED_A1);
        return builder;
    }

    enum Foo {
        BAR,
        BAZ,
        AUTO_ADDED_A1,
        AUTO_ADDED_A2
    }
}
