/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.util.localeresolution;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Locale;
import java.util.Optional;

/**
 * Generic implementation of {@link io.micronaut.core.util.LocaleResolver} for fixed locale resolution.
 *
 * @author Sergio del Amo
 * @since 2.3.0
 * @param <T> The context object which will be used to resolve the locale
 */
public class FixedLocaleResolver<T> extends DefaultLocaleResolver<T> {

    protected final Locale locale;

    /**
     *
     * @param localeResolutionConfiguration Locale Resolution configuration
     */
    public FixedLocaleResolver(LocaleResolutionConfiguration localeResolutionConfiguration) {
        super(localeResolutionConfiguration);
        this.locale = localeResolutionConfiguration.getFixed()
                .orElseThrow(() -> new IllegalArgumentException("The fixed locale must be set"));
    }

    @Override
    public Optional<Locale> resolve(@NonNull T context) {
        return Optional.of(locale);
    }
}
