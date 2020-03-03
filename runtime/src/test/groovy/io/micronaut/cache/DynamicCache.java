/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cache;

import io.micronaut.core.type.Argument;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class DynamicCache implements SyncCache<Map> {

    @Nonnull
    @Override
    public <T> Optional<T> get(@Nonnull Object key, @Nonnull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> T get(@Nonnull Object key, @Nonnull Argument<T> requiredType, @Nonnull Supplier<T> supplier) {
        return null;
    }

    @Nonnull
    @Override
    public <T> Optional<T> putIfAbsent(@Nonnull Object key, @Nonnull T value) {
        return Optional.empty();
    }

    @Override
    public void put(@Nonnull Object key, @Nonnull Object value) {

    }

    @Override
    public void invalidate(@Nonnull Object key) {

    }

    @Override
    public void invalidateAll() {

    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Map getNativeCache() {
        return null;
    }
}
