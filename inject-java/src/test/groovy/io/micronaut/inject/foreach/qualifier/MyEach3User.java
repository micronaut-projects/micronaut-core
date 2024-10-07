package io.micronaut.inject.foreach.qualifier;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;

@Requires(property = "spec", value = "EachBeanQualifierSpec")
@Singleton
public class MyEach3User {

    private final List<MyEach3> beans;

    public MyEach3User(List<MyEach3> beans) {
        this.beans = beans;
    }

    public List<MyEach3> getAll() {
        return beans;
    }
}
