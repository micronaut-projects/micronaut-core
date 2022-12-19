/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.tck;

import io.micronaut.http.HttpRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Defines a HTTP Server Test Scenario.
 * @author Sergio del Amo
 * @since 3.8.0
 */
public final class TestScenario {
    private final String specName;
    private final Map<String, Object> configuration;

    private final HttpRequest<?> request;
    private final BiConsumer<ServerUnderTest, HttpRequest<?>> assertion;

    private TestScenario(String specName,
                         Map<String, Object> configuration,
                         HttpRequest<?> request,
                         BiConsumer<ServerUnderTest, HttpRequest<?>> assertion) {
        this.specName = specName;
        this.configuration = configuration;
        this.request = request;
        this.assertion = assertion;
    }

    /**
     *
     * @return A Test Scenario builder.
     */
    public static TestScenario.Builder builder() {
        return new Builder();
    }

    private void run() throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(specName, configuration)) {
            if (assertion != null) {
                assertion.accept(server, request);
            }
        }
    }

    /**
     * Test Scenario Builder.
     */
    public static class Builder {

        private Map<String, Object> configuration;

        private String specName;

        private BiConsumer<ServerUnderTest, HttpRequest<?>> assertion;

        private HttpRequest<?> request;

        /**
         *
         * @param specName Value for {@literal spec.name} property. Used to avoid bean pollution.
         * @return Test Scenario builder
         */
        public Builder specName(String specName) {
            this.specName = specName;
            return this;
        }

        /**
         *
         * @param request HTTP Request to be sent in the test scenario
         * @return The Test Scneario Builder
         */
        public Builder request(HttpRequest<?> request) {
            this.request =  request;
            return this;
        }

        /**
         *
         * @param configuration Test Scenario configuration
         * @return Test scenario builder
         */
        public Builder configuration(Map<String, Object> configuration) {
            this.configuration =  configuration;
            return this;
        }

        /**
         *
         * @param assertion Assertion for a request and server.
         * @return The Test Scenario Builder
         */
        public Builder assertion(BiConsumer<ServerUnderTest, HttpRequest<?>> assertion) {
            this.assertion =  assertion;
            return this;
        }

        /**
         *
         * @return Builds a Test scenario
         */
        private TestScenario build() {
            return new TestScenario(specName, configuration,
                Objects.requireNonNull(request),
                Objects.requireNonNull(assertion));
        }

        /**
         * Runs the Test Scneario.
         * @throws IOException Exception thrown while getting the server under test.
         */
        public void run() throws IOException {
            build().run();
        }
    }
}
