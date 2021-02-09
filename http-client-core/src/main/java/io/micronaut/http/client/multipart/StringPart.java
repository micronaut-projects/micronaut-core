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
package io.micronaut.http.client.multipart;

import io.micronaut.core.annotation.NonNull;

/**
 * A class representing a String {@link Part} in {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
class StringPart extends Part<String> {

    protected final String value;

    /**
     * @param name  parameter name
     * @param value String value
     */
    StringPart(String name, String value) {
        super(name);
        if (value == null) {
            this.value = "";
        } else {
            this.value = value;
        }
    }

    @Override
    String getContent() {
        return value;
    }

    @NonNull
    @Override
    <T> T getData(@NonNull MultipartDataFactory<T> factory) {
        return factory.createAttribute(name, value);
    }
}
