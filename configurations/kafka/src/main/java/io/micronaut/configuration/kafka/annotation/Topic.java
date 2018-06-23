/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.kafka.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.messaging.annotation.MessageMapping;

import java.lang.annotation.*;

/**
 * Method level annotation used to specify which topics should be subscribed to.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@Repeatable(Topics.class)
public @interface Topic {

    /**
     * @return The topics to subscribe to
     */
    @AliasFor(annotation = MessageMapping.class, member = "value")
    String[] value() default {};

    /**
     * @return The topic patterns to subscribe to
     */
    String[] patterns() default {};

}
