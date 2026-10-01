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
package io.micronaut.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares that a bean depends on the beans of the given types without injecting them.
 *
 * <p>Every bean of each listed type is created before the annotated bean is instantiated and, for singletons,
 * destroyed after the annotated bean has been destroyed. The annotation therefore controls both the creation and
 * the destruction order, which is otherwise only derived from the injection points of a bean. A bean that must
 * stop consuming messages before the publisher it feeds is closed can express that as:</p>
 *
 * <pre class="code">
 * &#064;Singleton
 * &#064;DependsOn(MessagePublisher.class)
 * class MessageConsumer {
 *
 *     &#064;PreDestroy
 *     void stop() {
 *         // the publisher is still open here
 *     }
 * }
 * </pre>
 *
 * <p>The annotation is an error when no bean of a listed type exists.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DependsOn {

    /**
     * The types of the beans this bean depends on.
     *
     * @return The bean types
     */
    Class<?>[] value();
}
