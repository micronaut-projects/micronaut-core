/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.injectionpoint.cacheablelazytarget;

import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Factory;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.injectionpoint.SomeAnn;

@Factory
public class ProxiedSomeFactory {

    @Any
    @CustomScope
    ProxiedSomeBean someBean(InjectionPoint<ProxiedSomeBean> someBean) {
        final String str = someBean
                .getAnnotationMetadata()
                .stringValue(SomeAnn.class)
                .orElse("no value");

        return new ProxiedSomeBean(str);
    }
}
