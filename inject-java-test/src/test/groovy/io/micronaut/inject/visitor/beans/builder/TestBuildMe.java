package io.micronaut.inject.visitor.beans.builder;

import io.micronaut.core.annotation.Introspected;

@Introspected(
    builder = @Introspected.IntrospectionBuilder(builderClass = TestBuildMe.Builder.class)
)
public class TestBuildMe {
    private final String name;
    private final int age;

    private TestBuildMe(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public static final class Builder {
        private String name;
        private int age;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public TestBuildMe build() {
            return new TestBuildMe(
                name,
                age
            );
        }
    }
}
