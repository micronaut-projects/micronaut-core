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
package io.micronaut.python.annotation.processing.test.javabases;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * A Java base whose overridable methods declare checked exceptions, extended by Python classes in
 * the tests.
 */
public abstract class CheckedBase {

    private final List<String> initialized = new ArrayList<>();

    public void initialize(String channel, String name) throws IOException {
        if (channel == null) {
            throw new IOException("no channel for " + name);
        }
        initialized.add(channel + ":" + name);
    }

    public abstract String load(String key) throws IOException, TimeoutException;

    public String run(String key) {
        try {
            return load(key);
        } catch (IOException e) {
            return "io:" + e.getMessage();
        } catch (TimeoutException e) {
            return "timeout:" + e.getMessage();
        }
    }

    public void mark(String entry) {
        initialized.add(entry);
    }

    public List<String> initialized() {
        return initialized;
    }
}
