package org.particleframework.inject.value;

import org.particleframework.context.annotation.Value;

import javax.inject.Singleton;

@Singleton
public class B {
    int fromConstructor;
    A a;

    public B(
        A a,
        @Value("foo.bar") int port
    ) {
        this.fromConstructor = port;
        this.a = a;
    }

    public int getFromConstructor() {
        return fromConstructor;
    }

    public void setFromConstructor(int fromConstructor) {
        this.fromConstructor = fromConstructor;
    }

    public A getA() {
        return a;
    }

    public void setA(A a) {
        this.a = a;
    }
}