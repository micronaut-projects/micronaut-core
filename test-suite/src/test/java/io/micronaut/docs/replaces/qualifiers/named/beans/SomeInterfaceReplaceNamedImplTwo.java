package io.micronaut.docs.replaces.qualifiers.named.beans;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Named("two")
@Singleton
public class SomeInterfaceReplaceNamedImplTwo implements SomeInterfaceReplaceNamed {
    @Override
    public void doSomething() {

    }
}
