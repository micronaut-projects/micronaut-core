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
package io.micronaut.scheduling.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation used to indicate which executor service a particular task should run on.
 *
 * <p>Micronaut will by default run end user operations in the same thread that executes the request.
 * This annotation can be used to indicate that a different thread should be used when scheduling
 * the execution of an operation.</p>
 *
 * <p>Used to, for example, offload blocking I/O operations to specifically configured thread pool.</p>
 *
 * @author graemerocher
 * @since 2.0
 * @see io.micronaut.scheduling.TaskExecutors#IO
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface ScheduleOn {
    /**
     * @return The name of a configured executor service.
     * @see io.micronaut.scheduling.TaskExecutors#IO
     */
    String value();
}
