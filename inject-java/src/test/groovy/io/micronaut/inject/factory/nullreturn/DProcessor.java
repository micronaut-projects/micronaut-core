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
package io.micronaut.inject.factory.nullreturn;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.exceptions.DisabledBeanException;

import java.util.concurrent.atomic.AtomicInteger;

@EachBean(D.class)
public class DProcessor {

    static AtomicInteger constructed = new AtomicInteger();

    private final D d;

    DProcessor(@Nullable D d) {
        if (d == null) {
            throw new DisabledBeanException("Null D");
        }
        this.d = d;
        constructed.incrementAndGet();
    }
}
