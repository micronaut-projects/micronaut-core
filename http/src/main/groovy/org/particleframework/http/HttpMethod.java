/*
 * Copyright 2017 original authors
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
package org.particleframework.http;


/**
 * An enum containing the valid HTTP methods. See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public enum HttpMethod implements CharSequence {
    OPTIONS,
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    CONNECT;

    @Override
    public int length() {
        return name().length();
    }

    @Override
    public char charAt(int index) {
        return name().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return name().subSequence(start, end);
    }
}