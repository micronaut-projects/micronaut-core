/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.context.env;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Default {@link io.micronaut.context.env.PropertyPlaceholderResolver.Placeholder} implementation.
 *
 * @author graemerocher
 * @since 1.0
 */
class DefaultPlaceholder implements PropertyPlaceholderResolver.Placeholder {

    private final String property;
    private final String defaultValue;

    /**
     * Default constructor.
     *
     * @param property The property
     * @param defaultValue The default value
     */
    DefaultPlaceholder(String property, @Nullable String defaultValue) {
        this.property = property;
        this.defaultValue = defaultValue;
    }

    @Override
    public String getProperty() {
        return property;
    }

    @Override
    public Optional<String> getDefaultValue() {
        return Optional.ofNullable(defaultValue);
    }
}
