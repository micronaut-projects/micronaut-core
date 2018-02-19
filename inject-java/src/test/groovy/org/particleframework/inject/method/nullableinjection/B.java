package org.particleframework.inject.method.nullableinjection;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

public class B {
    A a;

    @Inject
    public void setA(@Nullable A a) {
        this.a = a;
    }

    A getA() {
        return this.a;
    }

}
