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
package io.micronaut.python.compiler;

/**
 * A Java API whose members are named after Python keywords, in the style of {@code Email.builder().from(...)}:
 * the builder is a foreign object returned from Java, so Python only sees it at runtime.
 */
public final class KeywordMessage {

    private final String from;
    private final String to;

    private KeywordMessage(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    /**
     * Builder with a {@code from} member.
     */
    public static final class Builder {
        private String from;
        private String to;

        private Builder() {
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public KeywordMessage build() {
            return new KeywordMessage(from, to);
        }
    }
}
