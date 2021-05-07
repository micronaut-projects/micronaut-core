package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;

@ConfigurationProperties("outer")
public class OuterWithIterableInnerProperties {

    private String name;
    private Iterable<InnerEach> inner;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Iterable<InnerEach> getIterableInner() {
        return inner;
    }

    public void setIterableInner(Iterable<InnerEach> inner) {
        this.inner = inner;
    }

    @EachProperty("iterable-inner")
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
