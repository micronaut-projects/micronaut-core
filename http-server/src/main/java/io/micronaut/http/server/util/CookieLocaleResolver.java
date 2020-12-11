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
public class CookieLocaleResolver implements LocaleResolver {

    private final String cookieName;
    private final Locale defaultLocale;

    public CookieLocaleResolver(HttpServerConfiguration serverConfiguration) {
        HttpServerConfiguration.LocaleResolutionConfiguration resolutionConfiguration = Objects.requireNonNull(serverConfiguration.getLocaleResolution());

        this.cookieName = resolutionConfiguration.getCookieName()
                .orElseThrow(() -> new IllegalArgumentException("The locale cookie name must be set"));
        this.defaultLocale = resolutionConfiguration.getDefaultLocale();
    }

    @Override
    public Optional<Locale> resolve(@NonNull HttpRequest<?> request) {
        return request.getCookies().get(cookieName, Locale.class);
    }

    @Override
    public Locale resolveOrDefault(@NonNull HttpRequest<?> request) {
        return resolve(request).orElse(defaultLocale);
    }

}
