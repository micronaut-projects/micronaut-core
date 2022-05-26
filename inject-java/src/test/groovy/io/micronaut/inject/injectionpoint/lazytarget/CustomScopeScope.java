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
package io.micronaut.inject.injectionpoint.lazytarget;

import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class CustomScopeScope implements CustomScope<io.micronaut.inject.injectionpoint.lazytarget.CustomScope> {
    @Override
    public Class<io.micronaut.inject.injectionpoint.lazytarget.CustomScope> annotationType() {
        return io.micronaut.inject.injectionpoint.lazytarget.CustomScope.class;
    }

    @Override
    public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        return creationContext.create().bean();
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        return Optional.empty();
    }
}
