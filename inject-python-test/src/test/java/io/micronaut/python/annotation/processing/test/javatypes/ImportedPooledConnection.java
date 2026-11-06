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
package io.micronaut.python.annotation.processing.test.javatypes;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;

/**
 * A concrete Java class Python code imports, creates with arguments and checks with isinstance.
 */
@Prototype
public class ImportedPooledConnection {

    private final String url;

    public ImportedPooledConnection(@Parameter String url) {
        this.url = url;
    }

    public String url() {
        return url;
    }
}
