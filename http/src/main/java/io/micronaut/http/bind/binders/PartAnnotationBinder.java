package io.micronaut.http.bind.binders;

import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Part;

/**
 * Skips binding parts because they should be handled by a multipart processor.
 *
 * @param <T> The part type
 * @author James Kleeh
 * @since 3.6.4
 */
public class PartAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<Part, T, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<Part, T> {

    public PartAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        //noinspection unchecked
        return BindingResult.UNSATISFIED;
    }

    @Override
    public Class<Part> getAnnotationType() {
        return Part.class;
    }
}
