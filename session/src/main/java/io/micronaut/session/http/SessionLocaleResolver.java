package io.micronaut.session.http;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.util.LocaleResolver;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Singleton
@Requires(property = HttpServerConfiguration.LocaleResolutionConfiguration.PREFIX + ".session-attribute")
public class SessionLocaleResolver implements LocaleResolver {

    private final String sessionAttribute;
    private final Locale defaultLocale;

    public SessionLocaleResolver(HttpServerConfiguration serverConfiguration) {
        HttpServerConfiguration.LocaleResolutionConfiguration resolutionConfiguration = Objects.requireNonNull(serverConfiguration.getLocaleResolution());

        this.sessionAttribute = resolutionConfiguration.getSessionAttribute()
                .orElseThrow(() -> new IllegalArgumentException("The session attribute must be set"));
        this.defaultLocale = resolutionConfiguration.getDefaultLocale();
    }

    @Override
    public Optional<Locale> resolve(@NonNull HttpRequest<?> request) {
        return SessionForRequest.find(request)
                .flatMap(session -> session.get(sessionAttribute, Locale.class));
    }

    @Override
    public Locale resolveOrDefault(@NonNull HttpRequest<?> request) {
        return resolve(request).orElse(defaultLocale);
    }
}
