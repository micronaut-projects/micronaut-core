/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.jackson.databind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonMapperSupplier;

/**
 * Implementation of {@link JsonMapperSupplier} for Jackson.
 *
 * @author Graeme Rocher
 * @since 4.0.0
 */
@Internal
public final class JacksonDatabindMapperSupplier implements JsonMapperSupplier {
    @Override
    public JsonMapper get() {
        return new JacksonDatabindMapper();
    }
}
