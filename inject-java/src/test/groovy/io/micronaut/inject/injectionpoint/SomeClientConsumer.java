package io.micronaut.inject.injectionpoint;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SomeClientConsumer {

    private SomeClient fromConstructor;

    @Inject
    @SomeAnn("two")
    SomeClient fromField;

    private SomeClient  fromMethod;

    public SomeClientConsumer(@SomeAnn("one") SomeClient fromConstructor) {
        this.fromConstructor = fromConstructor;
    }

    @Inject
    public void setFromMethod(@SomeAnn("three") SomeClient fromMethod) {
        this.fromMethod = fromMethod;
    }

    public SomeClient getFromConstructor() {
        return fromConstructor;
    }

    public SomeClient getFromField() {
        return fromField;
    }

    public SomeClient getFromMethod() {
        return fromMethod;
    }
}
