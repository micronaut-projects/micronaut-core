package io.micronaut.inject.visitor.beans.builder;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = TestBuildMe6.Builder.class)
public class TestBuildMe6 {
    private final String name;
    private final int age;

    private TestBuildMe6(String name, int age) {
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

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public TestBuildMe6 build() {
            return new TestBuildMe6(
                name,
                age
            );
        }
    }
}
