package io.micronaut.docs.replaces.qualifiers.annotations.beans;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.docs.replaces.qualifiers.annotations.One;
import jakarta.inject.Singleton;

@Singleton
@Replaces(value = SomeInterfaceReplaceQualifiers.class, qualifier = One.class)
@One
public class SomeInterfaceReplaceQualifiersImplOneReplacement implements SomeInterfaceReplaceQualifiers
{
    @Override
    public void doSomething()
    {

    }
}
