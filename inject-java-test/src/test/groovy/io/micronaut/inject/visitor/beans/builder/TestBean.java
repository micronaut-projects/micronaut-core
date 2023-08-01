package io.micronaut.inject.visitor.beans.builder;

import io.micronaut.core.annotation.Introspected;

import java.util.List;
import java.util.Objects;

@Introspected
public class TestBean {
    private final String name;
    private int age;
    private List<String> nickNames;

    public TestBean(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public List<String> getNickNames() {
        return nickNames;
    }

    public void setNickNames(List<String> nickNames) {
        this.nickNames = nickNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestBean testBean = (TestBean) o;
        return age == testBean.age && Objects.equals(name, testBean.name) && Objects.equals(nickNames, testBean.nickNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, nickNames);
    }
}
