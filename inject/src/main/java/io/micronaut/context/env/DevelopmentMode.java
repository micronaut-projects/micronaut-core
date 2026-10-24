/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.env;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;

/**
 * The development mode of a running application.
 *
 * <p>Development mode is switched on by a development launcher (for example the {@code micronaut-dev}
 * module) that keeps the process alive across restarts of the application context and reloads
 * application classes. Components that would otherwise terminate the JVM, such as the file watch
 * restart listener or the startup failure handling of the {@code Micronaut} launcher, do not do so
 * while development mode is active, because the launcher owns the process.</p>
 *
 * <p>Development mode is not the same as the {@link Environment#DEVELOPMENT} environment. The environment
 * is a configuration profile any run may activate; development mode is only active when a launcher
 * sets the {@link #PROPERTY} property to {@code true}, in any case.</p>
 *
 * <p>Beans that only run in development mode, or only outside it, are annotated with {@link DevelopmentActive} or
 * {@link DevelopmentInactive}.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class DevelopmentMode {

    /**
     * The property a development launcher sets to {@code true} to switch on development mode.
     */
    public static final String PROPERTY = "micronaut.dev.enabled";

    /**
     * The property the test mode of a development launcher sets to {@code true} on the application contexts its
     * tests start. Test mode does not switch on development mode for them, since a test runs as it would in the
     * build, but a module can keep what is expensive to create, and independent of the context, warm across the
     * runs.
     *
     * @since 5.3.0
     */
    public static final String TEST_PROPERTY = "micronaut.dev.test.enabled";

    private DevelopmentMode() {
    }

    /**
     * Whether the given property resolver belongs to an application context a test started in the test mode of a
     * development launcher.
     *
     * @param propertyResolver The property resolver, typically the environment
     * @return True if the test mode of a development launcher started the context
     * @since 5.3.0
     */
    public static boolean isTestMode(PropertyResolver propertyResolver) {
        return propertyResolver.getProperty(TEST_PROPERTY, String.class).map(StringUtils.TRUE::equalsIgnoreCase).orElse(false);
    }

    /**
     * Whether development mode is active for the given property resolver.
     *
     * @param propertyResolver The property resolver, typically the environment
     * @return True if development mode is active
     */
    public static boolean isEnabled(PropertyResolver propertyResolver) {
        // the same test as the @Requires of DevelopmentActive and DevelopmentInactive
        return propertyResolver.getProperty(PROPERTY, String.class).map(StringUtils.TRUE::equalsIgnoreCase).orElse(false);
    }

    /**
     * Whether development mode is active according to the system properties.
     *
     * <p>A launcher sets the system property before the application context exists, so components that
     * run before the environment is available can consult it.</p>
     *
     * @return True if the system property is set to {@code true}
     */
    public static boolean isEnabledBySystemProperty() {
        return StringUtils.TRUE.equalsIgnoreCase(System.getProperty(PROPERTY));
    }
}
