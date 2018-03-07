package io.micronaut.http.server.binding.binders;

import io.micronaut.http.annotation.Body;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.core.type.Argument;

import java.nio.charset.Charset;
import java.util.*;

/**
 * Binds a String body argument
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultBodyAnnotationBinder<T> implements BodyArgumentBinder<T> {

    protected final ConversionService<?> conversionService;

    public DefaultBodyAnnotationBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<Body> getAnnotationType() {
        return Body.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        Optional<?> body = source.getBody();
        if(!body.isPresent()) {
            //noinspection unchecked
            return BindingResult.EMPTY;
        }
        else {
            Object o = body.get();
            Optional<T> converted = conversionService.convert(o, context);
            final Optional<ConversionError> lastError = context.getLastError();
            //noinspection OptionalIsPresent
            if(lastError.isPresent()) {
                return new BindingResult<T>() {
                    @Override
                    public Optional<T> getValue() {
                        return Optional.empty();
                    }

                    @Override
                    public List<ConversionError> getConversionErrors() {
                        return Collections.singletonList(lastError.get());
                    }
                };
            }
            else {
                return () -> converted;
            }
        }
    }
}
