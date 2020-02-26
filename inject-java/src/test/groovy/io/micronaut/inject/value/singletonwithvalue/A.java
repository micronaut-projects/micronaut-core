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
package io.micronaut.inject.value.singletonwithvalue;

import io.micronaut.context.annotation.Value;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URL;
import java.util.Optional;

@Singleton
public class A {
    int fromConstructor;

    public A(
        @Value("${foo.bar}") int port) {
        this.fromConstructor = port;
    }

    @Value("${camelCase.URL}")
    URL url;

    @Value("${foo.bar}")
    Optional<Integer> optionalPort;

    @Value("${foo.another}")
    Optional<Integer> optionalPort2;

    @Value("${foo.bar}")
    int port;

    private int anotherPort;

    @Value("${foo.bar}")
    protected int fieldPort;

    @Value("${default.port:9090}")
    protected int defaultPort;

    @Inject
    void setAnotherPort(@Value("${foo.bar}") int port) {
        anotherPort = port;
    }

    int getAnotherPort() {
        return anotherPort;
    }

    int getFieldPort() {
        return fieldPort;
    }

    int getDefaultPort() {
        return defaultPort;
    }

    public int getFromConstructor() {
        return fromConstructor;
    }

    public void setFromConstructor(int fromConstructor) {
        this.fromConstructor = fromConstructor;
    }

    public Optional<Integer> getOptionalPort() {
        return optionalPort;
    }

    public void setOptionalPort(Optional<Integer> optionalPort) {
        this.optionalPort = optionalPort;
    }

    public Optional<Integer> getOptionalPort2() {
        return optionalPort2;
    }

    public void setOptionalPort2(Optional<Integer> optionalPort2) {
        this.optionalPort2 = optionalPort2;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setFieldPort(int fieldPort) {
        this.fieldPort = fieldPort;
    }

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }
}
