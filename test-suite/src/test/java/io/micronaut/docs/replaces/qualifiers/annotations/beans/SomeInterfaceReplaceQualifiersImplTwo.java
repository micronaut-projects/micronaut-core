package io.micronaut.docs.replaces.qualifiers.annotations.beans;

import io.micronaut.docs.replaces.qualifiers.annotations.Two;
import jakarta.inject.Singleton;

@Two
@Singleton
public class SomeInterfaceReplaceQualifiersImplTwo implements SomeInterfaceReplaceQualifiers
{
    @Override
    public void doSomething()
    {

    }
}
