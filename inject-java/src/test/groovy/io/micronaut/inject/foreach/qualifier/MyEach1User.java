package io.micronaut.inject.foreach.qualifier;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;

@Requires(property = "spec", value = "EachBeanQualifierSpec")
@Singleton
public class MyEach1User {

    private final List<MyEach1> myEach1s;

    public MyEach1User(List<MyEach1> myEach1s) {
        this.myEach1s = myEach1s;
    }

    public List<MyEach1> getAll() {
        return myEach1s;
    }
}
