package io.micronaut.web.router.version.resolution;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.web.router.version.RoutesVersioningConfiguration;

import java.util.Collections;
import java.util.List;

import static io.micronaut.web.router.version.resolution.ParameterVersionResolverConfiguration.PREFIX;

@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class ParameterVersionResolverConfiguration implements Toggleable {

    public static final String PREFIX = RoutesVersioningConfiguration.PREFIX + ".parameter";
    public static final String DEFAULT_PARAMETER_NAME = "api-version";

    private boolean enabled;
    private List<String> names = Collections.singletonList(DEFAULT_PARAMETER_NAME);

    /**
     * @return The parameter names to search for the version.
     */
    public List<String> getNames() {
        return names;
    }

    /**
     * Sets which parameter should be searched for a version.
     *
     * @param names The parameter names
     */
    public void setNames(List<String> names) {
        this.names = names;
    }

    /**
     * @return {@code true} If parameter should be searched.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether parameter should be searched for a version.
     *
     * @param enabled True if parameter should be searched.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
