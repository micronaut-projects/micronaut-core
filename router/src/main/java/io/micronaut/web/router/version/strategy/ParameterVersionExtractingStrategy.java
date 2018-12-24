package io.micronaut.web.router.version.strategy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.version.RoutesVersioningConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static io.micronaut.web.router.version.RoutesVersioningConfiguration.ParameterBasedVersioningConfiguration.PREFIX;

/**
 * A {@link VersionExtractingStrategy} responsible for extracting version from {@link io.micronaut.http.HttpParameters}.
 */
@Singleton
@Requires(beans = RoutesVersioningConfiguration.class)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class ParameterVersionExtractingStrategy implements VersionExtractingStrategy {

    private final String parameter;


    /**
     * Creates a {@link VersionExtractingStrategy} to extract version from request parameter.
     *
     * @param configuration A configuration to pick correct request parameter name.
     */
    @Inject
    public ParameterVersionExtractingStrategy(RoutesVersioningConfiguration.ParameterBasedVersioningConfiguration configuration) {
        this.parameter = configuration.getName();
    }

    @Override
    public Optional<String> extract(HttpRequest<?> request) {
        return Optional.ofNullable(request.getParameters().get(parameter));
    }
}
