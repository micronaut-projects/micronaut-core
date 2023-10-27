package io.micronaut.docs.replaces.qualifiers.named.beans;

import jakarta.inject.Named;
import jakarta.inject.Singleton;


@Named("one")
@Singleton
public class SomeInterfaceReplaceNamedImplOne implements SomeInterfaceReplaceNamed
{
    @Override
    public void doSomething()
    {

    }
}
