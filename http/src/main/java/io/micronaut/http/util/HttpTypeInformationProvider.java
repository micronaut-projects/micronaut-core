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
package io.micronaut.http.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.TypeInformationProvider;
import io.micronaut.http.HttpResponse;

/**
 * Provide type information for HTTP response.
 *
 * @author graemerocher
 * @since 2.4.0
 */
@Internal
public final class HttpTypeInformationProvider implements TypeInformationProvider {
    @Override
    public boolean isWrapperType(Class<?> type) {
        return type == HttpResponse.class || TypeInformationProvider.super.isWrapperType(type);
    }
}
