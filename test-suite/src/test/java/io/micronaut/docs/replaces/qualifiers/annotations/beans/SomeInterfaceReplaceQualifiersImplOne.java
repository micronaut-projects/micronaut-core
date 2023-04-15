package io.micronaut.docs.replaces.qualifiers.annotations.beans;

import io.micronaut.docs.replaces.qualifiers.annotations.One;
import jakarta.inject.Singleton;


@One
@Singleton
public class SomeInterfaceReplaceQualifiersImplOne implements SomeInterfaceReplaceQualifiers
{
    @Override
    public void doSomething()
    {

    }
}
