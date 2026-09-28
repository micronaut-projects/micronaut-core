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

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Introspected;
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
 * sets the {@link #PROPERTY} property.</p>
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

    private DevelopmentMode() {
    }

    /**
     * Whether development mode is active for the given property resolver.
     *
     * @param propertyResolver The property resolver, typically the environment
     * @return True if development mode is active
     */
    public static boolean isEnabled(PropertyResolver propertyResolver) {
        return propertyResolver.getProperty(PROPERTY, Boolean.class).orElse(false);
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

    /**
     * A bean condition that holds while development mode is active. Use with
     * {@code @Requires(condition = DevelopmentMode.Active.class)}.
     */
    @Introspected
    public static final class Active implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            if (isEnabled(context)) {
                return true;
            }
            context.fail("Development mode is not active (" + PROPERTY + " is not true)");
            return false;
        }
    }

    /**
     * A bean condition that holds while development mode is not active. Use with
     * {@code @Requires(condition = DevelopmentMode.Inactive.class)} on beans that must not run under a
     * development launcher, such as ones that exit the JVM.
     */
    @Introspected
    public static final class Inactive implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            if (!isEnabled(context)) {
                return true;
            }
            context.fail("Development mode is active (" + PROPERTY + " is true)");
            return false;
        }
    }
}
