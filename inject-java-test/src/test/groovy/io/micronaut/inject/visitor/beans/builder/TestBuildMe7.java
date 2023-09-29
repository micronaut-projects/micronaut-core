package io.micronaut.inject.visitor.beans.builder;


import io.micronaut.core.annotation.Introspected;

@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = TestBuildMe7.Builder.class))
public class TestBuildMe7 {
    private final String name;
    private final int age;

    private TestBuildMe7(String name, int age) {
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

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public TestBuildMe7 build() {
            return new TestBuildMe7(
                name,
                age
            );
        }
    }
}
