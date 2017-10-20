package org.particleframework.http.server.binding.binders;

import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.annotation.Body;
import org.particleframework.core.type.Argument;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
    public Optional<T> bind(ArgumentConversionContext<T> context, HttpRequest source) {
        Object body = source.getBody();
        Argument<T> argument = context.getArgument();
        return conversionService.convert(body, argument.getType(), context);
    }
}
