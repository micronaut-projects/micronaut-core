package io.micronaut.docs.replaces.qualifiers.named.beans;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Replaces(value = SomeInterfaceReplaceNamed.class, named = "two")
@Named("two")
public class SomeInterfaceReplaceNamedImplTwoReplacement implements SomeInterfaceReplaceNamed
{
    @Override
    public void doSomething()
    {

    }
}
