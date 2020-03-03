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
package io.micronaut.runtime.event.annotation;

// tag::imports[]
import io.micronaut.aop.Adapter;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Indexed;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
// end::imports[]

/**
 * <p>An {@link Adapter} advice annotation that allows listening for events by implementing the {@link ApplicationEventListener} interface.</p>
 *
 * <p>For example:</p>
 *
 * <pre><code>
 *  {@literal @}EventListener
 *   public void onStartup(StartupEvent startupEvent) {
 *   }
 * </code></pre>
 *
 * @author graemerocher
 * @since 1.0
 */
// tag::clazz[]
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Adapter(ApplicationEventListener.class) // <1>
@Indexed(ApplicationEventListener.class)
public @interface EventListener {
}
// end::clazz[]
