/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cli.profile.commands

import groovy.transform.CompileStatic

@CompileStatic
enum SupportedLanguage {
    java("java"),
    groovy("groovy"),
    kotlin("kt")

    final String extension

    private SupportedLanguage(String extension) {
        this.extension = extension
    }

    static boolean isValidValue(String value) {
        values().collect { it.name() }.contains(value)
    }

    static Optional<SupportedLanguage> findValue(String value) {
        isValidValue(value) ? Optional.of(valueOf(value)) : Optional.<SupportedLanguage>empty()
    }
}
