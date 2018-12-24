package io.micronaut.web.router.version.strategy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.version.RoutesVersioningConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static io.micronaut.web.router.version.RoutesVersioningConfiguration.HeaderBasedVersioningConfiguration.PREFIX;

/**
 * A {@link VersionExtractingStrategy} responsible for extracting version from {@link io.micronaut.http.HttpHeaders}.
 */
@Singleton
@Requires(beans = RoutesVersioningConfiguration.class)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class HeaderVersionExtractingStrategy implements VersionExtractingStrategy {

    private final String header;

    /**
     * Creates a {@link VersionExtractingStrategy} to extract version from request header.
     *
     * @param configuration A configuration to pick correct request header name.
     */
    @Inject
    public HeaderVersionExtractingStrategy(RoutesVersioningConfiguration.HeaderBasedVersioningConfiguration configuration) {
        this.header = configuration.getName();
    }

    @Override
    public Optional<String> extract(HttpRequest<?> request) {
        return Optional.ofNullable(request.getHeaders().get(header));
    }
}
