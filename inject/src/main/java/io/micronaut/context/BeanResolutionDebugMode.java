package io.micronaut.context;

import io.micronaut.core.naming.NameUtils;
import java.util.Optional;

/**
 * Allows enabling more verbose debugging on bean resolution.
 */
public enum BeanResolutionDebugMode {
    /**
     * No debug enabled (the default).
     */
    NONE,

    /**
     * When log mode is enabled output will be
     * written to a logger named {@code io.micronaut.context.resolution}.
     */
    LOG,

    /**
     * With standard out debug output will be written to {@link System#out}.
     */
    STANDARD_OUT,

    /**
     * Interactive mode will leverage both {@link System#out} and {@link System#in}
     * to allow pausing and resuming bean resolution and inspecting state.
     */
    INTERACTIVE;

    private static final String SYS_PROP = "micronaut.context.debug.mode";
    private static final String ENV_VAR = "MICRONAUT_CONTEXT_DEBUG_MODE";

    /**
     * Obtains the default mode.
     *
     * @return The default mode
     */
    public static BeanResolutionDebugMode getDefaultMode() {
        String mode = Optional
            .ofNullable(System.getProperty(SYS_PROP))
            .orElseGet(() -> System.getenv(ENV_VAR));
        if (mode != null) {
            return BeanResolutionDebugMode
                        .valueOf(NameUtils.environmentName(mode));
        }
        return NONE;
    }
}
