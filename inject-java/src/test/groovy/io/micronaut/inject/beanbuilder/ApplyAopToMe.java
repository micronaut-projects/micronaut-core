package io.micronaut.inject.beanbuilder;

public class ApplyAopToMe {
    public String hello(String name) {
        return "Hello " + name;
    }

    public String plain(String name) {
        return "Hello " + name;
    }
}
