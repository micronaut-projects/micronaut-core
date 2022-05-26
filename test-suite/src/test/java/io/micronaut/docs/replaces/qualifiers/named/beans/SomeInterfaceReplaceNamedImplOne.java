package io.micronaut.docs.replaces.qualifiers.named.beans;

import io.micronaut.docs.replaces.qualifiers.annotations.One;
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
