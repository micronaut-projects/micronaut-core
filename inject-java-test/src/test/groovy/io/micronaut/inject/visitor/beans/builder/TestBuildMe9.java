package io.micronaut.inject.visitor.beans.builder;


import io.micronaut.core.annotation.Introspected;

import java.util.Optional;

@Introspected(
    builder = @Introspected.IntrospectionBuilder(builderClass = TestBuildMe9.Builder.class),
    targetPackage = "io.micronaut.inject.visitor.beans.builder.test"
)
public class TestBuildMe9 {
    private final String name;
    private final int age;

    private TestBuildMe9(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private int age;

        protected Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder name(Optional<String> name) {
            this.name = name.orElse(null);
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public TestBuildMe9 build() {
            return new TestBuildMe9(
                name,
                age
            );
        }
    }
}
