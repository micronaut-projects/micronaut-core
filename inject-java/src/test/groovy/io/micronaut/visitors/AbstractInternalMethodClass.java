package io.micronaut.visitors;

import io.micronaut.core.annotation.Internal;

public abstract class AbstractInternalMethodClass {

    @Internal
    public String foo() {
        return "foo";
    }

}
