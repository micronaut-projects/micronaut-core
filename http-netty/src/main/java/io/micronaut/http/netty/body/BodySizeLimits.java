/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Internal;

/**
 * Body size limits.
 *
 * @param maxBodySize max body size
 * @param maxBufferSize max buffer size
 */
@Internal
public record BodySizeLimits(
    long maxBodySize,
    long maxBufferSize
) {
    public static final BodySizeLimits UNLIMITED = new BodySizeLimits(Long.MAX_VALUE, Integer.MAX_VALUE);
}
