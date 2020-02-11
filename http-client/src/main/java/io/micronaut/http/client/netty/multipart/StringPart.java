/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.netty.multipart;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

/**
 * A class representing a String {@link Part} in {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
class StringPart extends Part {

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

    /**
     * Create an object of {@link InterfaceHttpData} to build Netty multipart request body.
     *
     * @see Part#getData(HttpRequest, HttpDataFactory)
     */
    @Override
    InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory) {
        return factory.createAttribute(request, name, value);
    }
}
