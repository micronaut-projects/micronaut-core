package io.micronaut.inject.lifecycle.beancreationlambda;

import javax.inject.Singleton;

@Singleton
public class B {
    String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
