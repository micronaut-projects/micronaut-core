package io.micronaut.web.router.version.resolution;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.web.router.version.RoutesVersioningConfiguration;

import java.util.Collections;
import java.util.List;

import static io.micronaut.web.router.version.resolution.HeaderVersionResolverConfiguration.PREFIX;

@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class HeaderVersionResolverConfiguration implements Toggleable {

    public static final String PREFIX = RoutesVersioningConfiguration.PREFIX + ".header";
    public static final String DEFAULT_HEADER_NAME = "X-API-VERSION";

    private boolean enabled;
    private List<String> names = Collections.singletonList(DEFAULT_HEADER_NAME);

    /**
     * @return The header names to search for the version.
     */
    public List<String> getNames() {
        return names;
    }

    /**
     * Sets which headers should be searched for a version.
     *
     * @param names The header names
     */
    public void setNames(List<String> names) {
        this.names = names;
    }

    /**
     * @return {@code true} If headers should be searched.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether headers should be searched for a version.
     *
     * @param enabled True if headers should be searched.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
