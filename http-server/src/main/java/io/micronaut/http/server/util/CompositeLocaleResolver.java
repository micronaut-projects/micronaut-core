package io.micronaut.http.server.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Primary;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Singleton
@Primary
public class CompositeLocaleResolver implements LocaleResolver {

    private final LocaleResolver[] localeResolvers;
    private final Locale defaultLocale;

    public CompositeLocaleResolver(LocaleResolver[] localeResolvers,
                                   HttpServerConfiguration serverConfiguration) {
        this.localeResolvers = localeResolvers;
        this.defaultLocale = Optional.ofNullable(serverConfiguration.getLocaleResolution())
                .map(HttpServerConfiguration.LocaleResolutionConfiguration::getDefaultLocale)
                .orElse(Locale.getDefault());
    }

    @Override
    public Optional<Locale> resolve(@NonNull HttpRequest<?> request) {
        return Arrays.stream(localeResolvers)
                .map(resolver -> resolver.resolve(request))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    @Override
    public Locale resolveOrDefault(@NonNull HttpRequest<?> request) {
        return resolve(request).orElse(defaultLocale);
    }
}
