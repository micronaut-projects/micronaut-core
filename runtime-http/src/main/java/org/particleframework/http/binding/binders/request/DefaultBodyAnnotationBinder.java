package org.particleframework.http.binding.binders.request;

import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.binding.annotation.Body;
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

    protected final ConversionService conversionService;

    public DefaultBodyAnnotationBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<Body> getAnnotationType() {
        return Body.class;
    }

    @Override
    public Optional<T> bind(Argument<T> argument, HttpRequest source) {
        Object body = source.getBody();
        return conversionService.convert(body, argument.getType(), new ConversionContext() {
            @Override
            public Map<String, Class> getTypeVariables() {
                return argument.getTypeParameters();
            }

            @Override
            public Locale getLocale() {
                return source.getLocale();
            }

            @Override
            public Charset getCharset() {
                return source.getCharacterEncoding();
            }
        });
    }
}
