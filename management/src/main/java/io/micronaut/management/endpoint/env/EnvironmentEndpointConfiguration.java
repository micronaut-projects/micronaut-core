package io.micronaut.management.endpoint.env;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;


/**
 * Configuration specific to the {@link EnvironmentEndpoint}.
 * Allows customizing which sections of the environment information is displayed.
 */
@ConfigurationProperties("endpoints.env")
public class EnvironmentEndpointConfiguration {

    /**
     * Default keys to be displayed.
     */
    public static final List<String> DEFAULT_ACTIVE_SECTIONS = List.of(EnvironmentEndpoint.ACTIVE_ENVIRONMENTS_KEY,
        EnvironmentEndpoint.PACKAGES_KEY, EnvironmentEndpoint.PROPERTY_SOURCES_KEY);

    private List<String> activeKeys = DEFAULT_ACTIVE_SECTIONS;

    /**
     * Gets the keys to be displayed by the environment endpoint.
     * Defaults to ["activeEnvironments", "packages", "propertySources"] if not configured.
     * Configurable via {@code endpoints.env.activeKeys}.
     *
     * @return The list of active sections.
     */
    public List<String> getActiveKeys() {
        return activeKeys;
    }

    /**
     * Sets the sections to be displayed by the environment endpoint.
     * Example: {@code endpoints.env.activeKeys=activeEnvironments,packages}
     *
     * @param activeKeys The list of sections. If an empty list is provided, no sections will be displayed.
     */
    public void setActiveKeys(List<String> activeKeys) {
        this.activeKeys = activeKeys;
    }
}
