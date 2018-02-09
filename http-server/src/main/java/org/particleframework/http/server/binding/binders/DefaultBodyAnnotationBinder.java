package org.particleframework.http.server.binding.binders;

import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionError;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.annotation.Body;
import org.particleframework.core.type.Argument;

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
