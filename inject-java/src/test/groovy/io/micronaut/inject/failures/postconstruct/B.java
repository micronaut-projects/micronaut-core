package io.micronaut.inject.failures.postconstruct;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class B {

    boolean setupComplete = false;
    boolean injectedFirst = false;

    @Inject
    protected A another;
    private A a;

    @Inject
    public void setA(A a ) {
        this.a = a;
    }

    public A getA() {
        return a;
    }

    @PostConstruct
    public void setup() {
        throw new RuntimeException("bad");
    }
}
