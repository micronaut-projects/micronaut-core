package io.micronaut.docs.replaces.qualifiers.named.beans;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
public class SomeInterfaceClientReplaceNamed {
    private final SomeInterfaceReplaceNamed someInterfaceOne;
    private final SomeInterfaceReplaceNamed someInterfaceTwo;

    public SomeInterfaceClientReplaceNamed(@Named("one") SomeInterfaceReplaceNamed someInterfaceOne,
                                           @Named("two") SomeInterfaceReplaceNamed someInterfaceTwo) {
        this.someInterfaceOne = someInterfaceOne;
        this.someInterfaceTwo = someInterfaceTwo;
    }

    public SomeInterfaceReplaceNamed getSomeInterfaceOne() {
        return someInterfaceOne;
    }

    public SomeInterfaceReplaceNamed getSomeInterfaceTwo() {
        return someInterfaceTwo;
    }
}
