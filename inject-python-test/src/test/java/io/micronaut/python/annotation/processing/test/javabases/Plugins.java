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

/**
 * A Java class hierarchy with interfaces at every level, extended by Python classes in the tests.
 */
public final class Plugins {

    private Plugins() {
    }

    public interface Named {
        String name();
    }

    public interface Plugin<T> {
        T configure(T value);
    }

    public abstract static class NamedPlugin implements Named {
        @Override
        public String name() {
            return getClass().getSimpleName();
        }
    }

    public abstract static class AbstractPlugin<T> extends NamedPlugin implements Plugin<T> {
    }
}
