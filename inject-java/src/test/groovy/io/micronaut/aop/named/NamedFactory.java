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
package io.micronaut.aop.named;

import io.micronaut.aop.Logged;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.runtime.context.scope.Refreshable;

import javax.inject.Named;
import javax.inject.Singleton;

@Factory
public class NamedFactory {

    @EachProperty(value = "aop.test.named", primary = "default")
    @Refreshable
    NamedInterface namedInterface(@Parameter String name) {
        return () -> name;
    }


    @Named("first")
    @Logged
    @Singleton
    OtherInterface first() {
        return () -> "first";
    }

    @Named("second")
    @Logged
    @Singleton
    OtherInterface second() {
        return () -> "second";
    }

    @EachProperty("other.interfaces")
    OtherInterface third(Config config, @Parameter String name) {
        return () -> name;
    }
}
