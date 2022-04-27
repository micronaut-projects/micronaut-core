package io.micronaut.docs.replaces.qualifiers.annotations.beans;

import io.micronaut.docs.replaces.qualifiers.annotations.One;
import io.micronaut.docs.replaces.qualifiers.annotations.Two;
import jakarta.inject.Singleton;

@Singleton
public class SomeInterfaceClientReplaceQualifiers
{
    private final SomeInterfaceReplaceQualifiers someInterfaceOne;
    private final SomeInterfaceReplaceQualifiers someInterfaceTwo;

    public SomeInterfaceClientReplaceQualifiers(@One SomeInterfaceReplaceQualifiers someInterfaceOne,
                                                @Two SomeInterfaceReplaceQualifiers someInterfaceTwo)
    {
        this.someInterfaceOne = someInterfaceOne;
        this.someInterfaceTwo = someInterfaceTwo;
    }

    public SomeInterfaceReplaceQualifiers getSomeInterfaceOne()
    {
        return someInterfaceOne;
    }

    public SomeInterfaceReplaceQualifiers getSomeInterfaceTwo()
    {
        return someInterfaceTwo;
    }
}
