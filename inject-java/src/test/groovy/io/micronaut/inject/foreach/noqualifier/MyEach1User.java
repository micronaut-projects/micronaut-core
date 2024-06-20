package io.micronaut.inject.foreach.noqualifier;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;

@Requires(property = "spec", value = "EachBeanNoQualifierSpec")
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
