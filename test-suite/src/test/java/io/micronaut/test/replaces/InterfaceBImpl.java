package io.micronaut.test.replaces;

import javax.inject.Singleton;

@Singleton
public class InterfaceBImpl implements InterfaceB {
    @Override
    public String doStuff() {
        return "real";
    }
}
