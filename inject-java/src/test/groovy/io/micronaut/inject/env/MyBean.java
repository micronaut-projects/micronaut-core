package io.micronaut.inject.env;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Value;

import javax.inject.Inject;

@Prototype
public class MyBean {

    private String value;

    public String getValue() {
        return value;
    }


    @Inject
    public void setValue(@Value("${my-bean.value}") String value) {
        this.value = value;
    }
}
