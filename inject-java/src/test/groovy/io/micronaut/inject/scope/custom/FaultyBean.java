package io.micronaut.inject.scope.custom;

@AnotherConcurrentScope
public class FaultyBean {
    public FaultyBean() {
        throw new RuntimeException("Bad things");
    }
}
