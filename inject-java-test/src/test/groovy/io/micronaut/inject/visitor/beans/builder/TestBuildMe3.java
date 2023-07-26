package io.micronaut.inject.visitor.beans.builder;

import io.micronaut.core.annotation.Introspected;

import java.util.Objects;

@Introspected(
    builder = @Introspected.IntrospectionBuilder(builderMethod = "builder")
)
public class TestBuildMe3 {
    private final String name;
    private final int age;

    private final String company;

    private TestBuildMe3(String name, int age, String company) {
        this.name = name;
        this.age = age;
        this.company = company;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestBuildMe3 that = (TestBuildMe3) o;
        return age == that.age && Objects.equals(name, that.name) && Objects.equals(company, that.company);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, company);
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }


    public String getCompany() {
        return company;
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

        public TestBuildMe3 build(String company) {
            return new TestBuildMe3(
                name,
                age,
                company);
        }
    }
}
