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
 * An outer class with a static nested abstract base, as gRPC generates them
 * ({@code GreeterGrpc.GreeterImplBase}).
 */
public final class Services {

    private Services() {
    }

    /**
     * The nested base.
     */
    public abstract static class ServiceBase {

        private final String service;

        protected ServiceBase(String service) {
            this.service = service;
        }

        public abstract String handle(String request);

        public String describe() {
            return service + ":" + handle("ping");
        }
    }
}
