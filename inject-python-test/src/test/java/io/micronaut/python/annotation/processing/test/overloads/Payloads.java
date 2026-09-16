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
package io.micronaut.python.annotation.processing.test.overloads;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Overloads taking a byte array, a string or a stream, as {@code ObjectMapper.readValue} does.
 */
public final class Payloads {

    private Payloads() {
    }

    public static String read(byte[] payload) {
        return "bytes:" + new String(payload, StandardCharsets.UTF_8);
    }

    public static String read(String payload) {
        return "string:" + payload;
    }

    public static String read(InputStream payload) throws IOException {
        return "stream:" + new String(payload.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static String read(Object payload) {
        return "object:" + payload;
    }

    public static int size(byte[] payload) {
        return payload.length;
    }
}
