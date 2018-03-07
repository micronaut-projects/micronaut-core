package io.micronaut.inject.lifecycle.beanwithpredestroy;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.Closeable;

@Singleton
public class B implements Closeable {

    boolean noArgsDestroyCalled = false;
    boolean injectedDestroyCalled = false;

    @Inject
    protected A another;
    private A a;

    @Inject
    void setA(A a ) {
        this.a = a;
    }

    A getA() {
        return a;
    }

    @PreDestroy
    public void close() {
        noArgsDestroyCalled = true;
    }

    @PreDestroy
    void another(C c) {
        if(c != null) {
            injectedDestroyCalled = true;
        }
    }
}
