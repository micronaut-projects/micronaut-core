package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.naming.Named;

@Introspected
public interface TestNamed extends Named {
    void setName(String name);
}
