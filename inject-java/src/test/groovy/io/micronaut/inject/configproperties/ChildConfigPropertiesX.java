package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.inject.configproperties.other.ParentConfigProperties;

@ConfigurationProperties("child")
public class ChildConfigPropertiesX extends ParentConfigProperties {

    private Integer age;

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}