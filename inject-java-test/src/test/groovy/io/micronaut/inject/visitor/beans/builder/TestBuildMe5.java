package io.micronaut.inject.visitor.beans.builder;


public class TestBuildMe5 {
    private final String name;
    private final int age;

    private TestBuildMe5(String name, int age) {
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

        public TestBuildMe5 build() {
            return new TestBuildMe5(
                name,
                age
            );
        }
    }
}
