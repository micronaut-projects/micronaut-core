package io.micronaut.docs.replaces.qualifiers.annotations.beans;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.docs.replaces.qualifiers.annotations.Two;
import jakarta.inject.Singleton;

@Singleton
@Replaces(value = SomeInterfaceReplaceQualifiers.class, qualifier = Two.class)
@Two
public class SomeInterfaceReplaceQualifiersImplTwoReplacement implements SomeInterfaceReplaceQualifiers
{
    @Override
    public void doSomething()
    {

    }
}
