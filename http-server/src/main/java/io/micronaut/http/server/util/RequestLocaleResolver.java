package io.micronaut.http.server.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;

@Singleton
@Requires(property = HttpServerConfiguration.LocaleResolutionConfiguration.PREFIX + ".header-resolution", notEquals = StringUtils.FALSE)
public class RequestLocaleResolver implements LocaleResolver {

    public static final Integer ORDER = 100;

    private final Locale defaultLocale;

    public RequestLocaleResolver(HttpServerConfiguration serverConfiguration) {
        this.defaultLocale = Optional.ofNullable(serverConfiguration.getLocaleResolution())
                .map(HttpServerConfiguration.LocaleResolutionConfiguration::getDefaultLocale)
                .orElse(Locale.getDefault());
    }

        @Override
    public Optional<Locale> resolve(@NonNull HttpRequest<?> request) {
        return request.getLocale();
    }

    @Override
    public Locale resolveOrDefault(@NonNull HttpRequest<?> request) {
        return resolve(request).orElse(defaultLocale);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
