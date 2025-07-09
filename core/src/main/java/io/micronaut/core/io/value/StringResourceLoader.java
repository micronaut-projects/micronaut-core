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
package io.micronaut.core.io.value;

import io.micronaut.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A {@link ResourceLoader} that returns a fixed string value.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
public final class StringResourceLoader extends ValueResourceLoader {
    private static final StringResourceLoader INSTANCE = new StringResourceLoader();

    private StringResourceLoader() {
        super("string:");
    }

    public static ResourceLoader getInstance() {
        return INSTANCE;
    }

    @Override
    protected Optional<byte[]> extract(String value) {
        return Optional.of(value.getBytes(StandardCharsets.UTF_8));
    }
}
