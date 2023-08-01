package io.micronaut.inject.visitor.beans.builder;

import io.micronaut.core.annotation.Introspected;

import java.util.Objects;

@Introspected(
    builder = @Introspected.IntrospectionBuilder(builderMethod = "builder")
)
public class TestBuildMe2 {
    private final String name;
    private final int age;

    private TestBuildMe2(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestBuildMe2 that = (TestBuildMe2) o;
        return age == that.age && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age);
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
        private Builder() {
        }

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

        public TestBuildMe2 build() {
            return new TestBuildMe2(
                name,
                age
            );
        }
    }
}
