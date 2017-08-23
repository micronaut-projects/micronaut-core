package org.particleframework.inject.factory.factorydefinition;

import javax.inject.Singleton;

@Singleton
public class A {
    String name = "A";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
