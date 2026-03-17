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

import java.util.Base64;
import java.util.Optional;

/**
 * A {@link ResourceLoader} that returns a fixed decoded base64 value.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
public final class Base64ResourceLoader extends ValueResourceLoader {
    private static final Base64ResourceLoader INSTANCE = new Base64ResourceLoader();

    private Base64ResourceLoader() {
        super("base64:");
    }

    public static ResourceLoader getInstance() {
        return INSTANCE;
    }

    @Override
    protected Optional<byte[]> extract(String value) {
        return Optional.of(Base64.getDecoder().decode(value));
    }
}
