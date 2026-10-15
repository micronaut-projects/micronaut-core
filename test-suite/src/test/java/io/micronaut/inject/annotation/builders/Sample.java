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
package io.micronaut.inject.annotation.builders;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Sample {
    String name();

    String label() default "label";

    int count() default 3;

    long big() default 4L;

    boolean flag() default true;

    double ratio() default 0.5;

    byte small() default 1;

    char letter() default 'x';

    short tiny() default 2;

    float fraction() default 1.5f;

    Class<?> type() default Object.class;

    Class<?>[] types() default {String.class, Integer.class};

    Color color() default Color.GREEN;

    Color[] colors() default {Color.RED, Color.BLUE};

    String[] names() default {"a", "b"};

    int[] numbers() default {1, 2, 3};

    Tag tag() default @Tag("default");

    Tag[] tags() default {@Tag("one"), @Tag(value = "two", weight = 2)};
}
