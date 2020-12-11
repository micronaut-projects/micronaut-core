package io.micronaut.http.server.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Singleton
@Requires(property = HttpServerConfiguration.LocaleResolutionConfiguration.PREFIX + ".fixed")
public class FixedLocaleResolver implements LocaleResolver {

    private final Locale locale;

    public FixedLocaleResolver(HttpServerConfiguration serverConfiguration) {
        HttpServerConfiguration.LocaleResolutionConfiguration resolutionConfiguration = Objects.requireNonNull(serverConfiguration.getLocaleResolution());

        this.locale = resolutionConfiguration.getFixed()
                .orElseThrow(() -> new IllegalArgumentException("The fixed locale must be set"));
    }

    @Override
    public Optional<Locale> resolve(@NonNull HttpRequest<?> request) {
        return Optional.of(locale);
    }

    @Override
    public Locale resolveOrDefault(@NonNull HttpRequest<?> request) {
        return locale;
    }
}
