package io.micronaut.test.replaces;

import javax.inject.Singleton;

@Singleton
public class InterfaceAImpl implements InterfaceA {
    @Override
    public String doStuff() {
        return "real-bean";
    }
}
