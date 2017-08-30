package org.particleframework.http.binding.binders.request.body;

import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.binding.binders.request.BodyArgumentBinder;
import org.particleframework.inject.Argument;

import java.util.Optional;

/**
 * Binds a String body argument
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JsonStringBodyBinder implements BodyArgumentBinder<String> {

    private final ConversionService conversionService;

    public JsonStringBodyBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.JSON;
    }

    @Override
    public Class<String> getArgumentType() {
        return String.class;
    }

    @Override
    public Optional<String> bind(Argument<String> argument, HttpRequest source) {
        Object body = source.getBody();
        return conversionService.convert(body, getArgumentType(), ConversionContext.of(source.getCharacterEncoding()));
    }
}
