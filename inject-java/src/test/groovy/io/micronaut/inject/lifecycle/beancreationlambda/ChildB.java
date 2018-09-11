package io.micronaut.inject.lifecycle.beancreationlambda;


public class ChildB extends B {
    B original;

    public ChildB(B original) {
        this.original = original;
    }

    public B getOriginal() {
        return original;
    }

    public void setOriginal(B original) {
        this.original = original;
    }
}
