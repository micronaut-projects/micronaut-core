/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.Internal;

/**
 * Makes Jackson builder methods visible to bean property resolution.
 *
 * @author Denis Stepanov
 * @since 5.1.4
 */
@Internal
public final class ToolsJsonPOJOBuilderAnnotationMapper extends AbstractJsonPOJOBuilderAnnotationMapper {

    @Override
    public String getName() {
        return "tools.jackson.databind.annotation.JsonPOJOBuilder";
    }
}
