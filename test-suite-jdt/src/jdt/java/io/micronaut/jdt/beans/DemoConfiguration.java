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
package io.micronaut.jdt.beans;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/**
 * The reproducer from micronaut-projects/micronaut-core#12658: the {@code @Parameter} component is
 * not the alphabetically first one, which used to lose a property when compiled with JDT.
 *
 * @param name the name of the configuration
 * @param mode the mode
 * @param enabled whether the configuration is enabled
 */
@EachProperty("demos")
public record DemoConfiguration(@Parameter String name, Mode mode, boolean enabled) {

    /**
     * The mode of a demo.
     */
    public enum Mode {
        FAST,
        SLOW
    }
}
