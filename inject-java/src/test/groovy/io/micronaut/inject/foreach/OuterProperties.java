package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;

import java.util.List;

@ConfigurationProperties("outer")
public class OuterProperties {

    private String name;
    private List<InnerEach> inner;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<InnerEach> getInner() {
        return inner;
    }

    public void setInner(List<InnerEach> inner) {
        this.inner = inner;
    }

    @EachProperty("inner")
    public static class InnerEach {
        private Integer age;

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }
}
