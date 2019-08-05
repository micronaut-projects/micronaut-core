package io.micronaut.inject.named;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class NamedFunctionBean {

    private final NamedFunction inputFromConstructor;
    private final NamedFunction outputFromConstructor;
    @Inject
    @Named("INPUT")
    private NamedFunction privateFieldInput;
    @Inject
    @Named("OUTPUT")
    private NamedFunction privateFieldOutput;
    public NamedFunctionBean(
            @Named("INPUT") NamedFunction inputFromConstructor,
            @Named("OUTPUT") NamedFunction outputFromConstructor) {
        this.inputFromConstructor = inputFromConstructor;
        this.outputFromConstructor = outputFromConstructor;
    }

    public NamedFunction getInputFromConstructor() {
        return inputFromConstructor;
    }

    public NamedFunction getOutputFromConstructor() {
        return outputFromConstructor;
    }

    public NamedFunction getPrivateFieldInput() {
        return privateFieldInput;
    }

    public NamedFunction getPrivateFieldOutput() {
        return privateFieldOutput;
    }
}
