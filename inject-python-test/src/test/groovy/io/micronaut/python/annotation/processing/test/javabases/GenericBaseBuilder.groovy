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
package io.micronaut.python.annotation.processing.test.javabases

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * A non-public generic builder base, as {@code io.nats.client.SubscribeOptions.Builder} is. Written
 * in Groovy because javac would add access bridges for its public methods to the public subclass,
 * which the compilers of some libraries do not.
 *
 * @param <B> The builder type
 */
@PackageScope
@CompileStatic
abstract class GenericBaseBuilder<B extends GenericBaseBuilder<B>> {

    protected String stream
    protected int configuration

    B stream(String stream) {
        this.stream = stream
        return self()
    }

    B configuration(int configuration) {
        this.configuration = configuration
        return self()
    }

    B configuration(String configuration) {
        this.configuration = configuration.length()
        return self()
    }

    protected abstract B self()
}
