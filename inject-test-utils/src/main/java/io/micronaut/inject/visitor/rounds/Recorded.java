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
package io.micronaut.inject.visitor.rounds;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * What {@link RoundsVisitor} puts on the beans it registers.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Recorded {

    /**
     * @return The classes of the round the bean was registered for
     */
    String[] classes() default {};

    /**
     * @return The callback that registered the bean
     */
    String phase();
}
