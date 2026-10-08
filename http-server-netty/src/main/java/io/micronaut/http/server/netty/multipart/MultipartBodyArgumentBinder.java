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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.multipart.FormFactory;

/**
 * Compatibility facade for the transport-independent multipart binder.
 *
 * @deprecated Use {@link io.micronaut.http.server.binding.MultipartBodyArgumentBinder}.
 * @since 1.3.0
 */
@Internal
@Deprecated(since = "5.3.0", forRemoval = true)
public class MultipartBodyArgumentBinder extends io.micronaut.http.server.binding.MultipartBodyArgumentBinder {
    /** @param formFactory Form utilities */
    public MultipartBodyArgumentBinder(BeanProvider<FormFactory> formFactory) {
        super(formFactory);
    }
}
