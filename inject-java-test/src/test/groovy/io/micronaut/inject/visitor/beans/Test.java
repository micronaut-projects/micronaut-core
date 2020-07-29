package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Test {

    private int[][] failingField;

    public Test() {
    }

    public int[][] getFailingField() {
        return failingField;
    }

    public Test setFailingField(int[][] failingField) {
        this.failingField = failingField;
        return this;
    }
}
