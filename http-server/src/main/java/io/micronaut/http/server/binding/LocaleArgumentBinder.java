package io.micronaut.http.server.binding;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.server.util.LocaleResolver;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;

@Singleton
public class LocaleArgumentBinder implements TypedRequestArgumentBinder<Locale> {

    private final LocaleResolver localeResolver;

    public LocaleArgumentBinder(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Override
    public Argument<Locale> argumentType() {
        return Argument.of(Locale.class);
    }

    @Override
    public BindingResult<Locale> bind(ArgumentConversionContext<Locale> context, HttpRequest<?> source) {
        final Optional<Locale> locale = localeResolver.resolve(source);
        if (locale.isPresent()) {
            return () -> locale;
        } else {
            return BindingResult.UNSATISFIED;
        }
    }
}
