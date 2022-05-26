/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.provider;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.type.TypeInformationProvider;
import jakarta.inject.Provider;

/**
 * Makes {@link io.micronaut.core.type.Argument#isWrapperType()} return true for Providers.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public final class ProviderTypeInformationProvider implements TypeInformationProvider {

    @Override
    public boolean isWrapperType(Class<?> type) {
        return BeanProvider.class == type ||
                Provider.class == type ||
                type.getName().equals("javax.inject.Provider");
    }
}
