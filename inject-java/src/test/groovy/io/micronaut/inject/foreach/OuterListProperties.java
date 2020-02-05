package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;

import java.util.List;

@ConfigurationProperties("outer-list")
public class OuterListProperties {

    private String name;
    private List<InnerEachList> innerList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<InnerEachList> getInnerList() {
        return innerList;
    }

    public void setInnerList(List<InnerEachList> innerList) {
        this.innerList = innerList;
    }

    @EachProperty(value = "inner-list", list = true)
    public static class InnerEachList {
        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        private Integer age;
    }
}
