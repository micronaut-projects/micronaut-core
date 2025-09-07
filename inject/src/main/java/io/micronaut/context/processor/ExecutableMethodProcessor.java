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
package io.micronaut.context.processor;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import java.lang.annotation.Annotation;

/**
 * <p>A class capable of processing an {@link io.micronaut.inject.ExecutableMethod} instances.</p>
 *
 * <p>The use case here is framework components that need to react to the presence of an annotation. For example given
 * the following annotation:</p>
 *
 * <pre class="code">
 * &#064;Executable(processOnStartup=true)
 * &#064;Retention(RUNTIME)
 * &#064;Target(ElementType.METHOD)
 * public &#064;interface Scheduled {
 * String cron()
 * }
 * </pre>
 *
 * <p>One could write a {@code ExecutableMethodProcessor} that processed all methods annotated with {@literal @}Scheduled:</p>
 *
 * <pre class="code">
 * {@code
 * public class MyProcessor implements ExecutableMethodProcessor<Scheduled> {
 * }}
 * </pre>
 * NOTE: The processor will only be invoked for methods that needs to be processed at startup.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Indexed(ExecutableMethodProcessor.class)
public interface ExecutableMethodProcessor<A extends Annotation> {

    /**
     * The process method will be called for every {@link ExecutableMethod} that is annotated with the type parameter A.
     *
     * @param beanDefinition The bean definition to process
     * @param method         The executable method
     */
    <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method);
}
