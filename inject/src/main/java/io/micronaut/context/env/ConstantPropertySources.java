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
package io.micronaut.context.env;

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * An optimization class which is used to register property sources
 * statically. This is typically used when converting "dynamic"
 * property sources like YAML files into "static" property sources
 * (Java configuration) at build time.
 * The list of static property sources is injected via this class.
 *
 * @since 3.2.0
 */
@Internal
public final class ConstantPropertySources {
    private final List<PropertySource> sources;

    public ConstantPropertySources(List<PropertySource> sources) {
        this.sources = sources;
    }

    List<PropertySource> getSources() {
        return sources;
    }
}
