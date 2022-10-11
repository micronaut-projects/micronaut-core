package io.micronaut.aop.compile;

import io.micronaut.context.annotation.Executable;

@Executable
public class SuperClass {

    protected boolean myBool() {
        return false;
    }

    protected int myInt() {
        return 12;
    }

    public double myDouble() {
        return 15.5;
    }

}
