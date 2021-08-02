package io.micronaut.inject.ast.beans;

import java.util.Objects;
import java.util.function.Consumer;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ConstructorElement;

public interface BeanConstructorElement extends ConstructorElement {

    /**
     * Process the bean parameters.
     *
     * @param parameterConsumer The parameter consumer
     * @return This bean method
     */
    default @NonNull BeanConstructorElement withParameters(@NonNull Consumer<BeanParameterElement[]> parameterConsumer) {
        Objects.requireNonNull(parameterConsumer, "The parameter consumer cannot be null");
        parameterConsumer.accept(getParameters());
        return this;
    }

    @NonNull
    @Override
    BeanParameterElement[] getParameters();
}
