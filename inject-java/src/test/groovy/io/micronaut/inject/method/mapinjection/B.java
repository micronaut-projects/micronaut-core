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
package io.micronaut.inject.method.mapinjection;

import io.micronaut.context.BeanContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class B {
    private Map<String, A> all;
    private LinkedHashMap<CharSequence, A> linked;
    private BeanContext beanContext;

    @Inject
    void setA(Map<String, A> a) {
        this.all = a;
    }

    @Inject
    private void setPrivate(LinkedHashMap<CharSequence, A> a, BeanContext beanContext) {
        this.linked = a;
    }

    Map<String, A> getAll() {
        return this.all;
    }

    public LinkedHashMap<CharSequence, A> getLinked() {
        return linked;
    }

    public BeanContext getBeanContext() {
        return beanContext;
    }
}
