/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.writer;

import java.io.IOException;
import java.io.Writer;

/**
 * An extension to {@link GeneratedFile} for generating source files.
 * A source generator is expected to generate sources for at least one
 * language (Java, Groovy or Kotlin).
 */
public interface GeneratedSourceFile extends GeneratedFile {
    /**
     * Generates the sources for a specific language.
     * @param language the language
     * @param consumer the code generating block
     * @throws IOException in case of I/O error
     */
    void visitLanguage(Language language, ThrowingConsumer<? super Writer> consumer) throws IOException;

    /**
     * A consumer which may throw an IOException
     * @param <T> the type of the consumed element
     */
    @FunctionalInterface
    interface ThrowingConsumer<T> {
        void accept(T t) throws IOException;
    }

    /**
     * The languages that are supported in source code generation.
     * Not all visitors may support all languages.
     */
    enum Language {
        JAVA("Java"),
        GROOVY("Groovy"),
        KOTLIN("Kotlin");

        private final String displayName;

        Language(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
