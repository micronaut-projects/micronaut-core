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
package io.micronaut.core.io.buffer;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ReferenceCounted {
    /**
     * Retain an additional reference to this object.  All retained references must be released, or there will be a leak.
     *
     * @return this
     */
    ByteBuffer retain();

    /**
     * Release a reference to this object.
     *
     * @return Whether the reference has been released
     * @throws IllegalStateException if the reference count is already 0
     */
    boolean release();
}
