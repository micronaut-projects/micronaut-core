package io.micronaut.core.bind;

import io.micronaut.core.convert.ConversionError;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class MappedBindingResult<T, R> implements ArgumentBinder.BindingResult<R> {
    private final ArgumentBinder.BindingResult<T> source;
    private final Function<T, ArgumentBinder.BindingResult<R>> function;
    private ArgumentBinder.BindingResult<R> second;

    MappedBindingResult(ArgumentBinder.BindingResult<T> source, Function<T, ArgumentBinder.BindingResult<R>> function) {
        this.source = source;
        this.function = function;
    }

    private ArgumentBinder.BindingResult<R> computeSecond() {
        if (second == null) {
            Optional<T> first = source.getValue();
            if (first.isPresent()) {
                second = function.apply(first.get());
            } else {
                second = (ArgumentBinder.BindingResult<R>) source;
            }
        }
        return second;
    }

    @Override
    public List<ConversionError> getConversionErrors() {
        List<ConversionError> conversionErrors = source.getConversionErrors();
        if (conversionErrors.isEmpty() && source.isSatisfied()) {
            conversionErrors = computeSecond().getConversionErrors();
        }
        return conversionErrors;
    }

    @Override
    public boolean isSatisfied() {
        return source.isSatisfied() && computeSecond().isSatisfied();
    }

    @Override
    public Optional<R> getValue() {
        return computeSecond().getValue();
    }
}
