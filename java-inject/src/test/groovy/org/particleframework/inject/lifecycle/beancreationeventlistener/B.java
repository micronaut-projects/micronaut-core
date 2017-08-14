package org.particleframework.inject.lifecycle.beancreationeventlistener;

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
