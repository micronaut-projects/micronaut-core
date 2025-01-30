/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.ConfigurationException;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Runtime implementation of {@link BeanConfiguration}.
 * @param packageName The package name
 * @param condition The condition
 */
record ConditionalBeanConfiguration(
    String packageName, Predicate<BeanContext> condition) implements BeanConfiguration {

    ConditionalBeanConfiguration {
        Objects.requireNonNull(packageName, "Package cannot be null");
        Objects.requireNonNull(condition, "Condition cannot be null");
        if (packageName.startsWith("io.micronaut.")) {
            throw new ConfigurationException("Custom bean configurations cannot be added for internal Micronaut packages: " + packageName);
        }
    }

    @Override
    public Package getPackage() {
        throw new UnsupportedOperationException("Package not retrievable");
    }

    @Override
    public String getName() {
        return packageName;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return condition.test(context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConditionalBeanConfiguration that = (ConditionalBeanConfiguration) o;
        return Objects.equals(packageName, that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(packageName);
    }
}
