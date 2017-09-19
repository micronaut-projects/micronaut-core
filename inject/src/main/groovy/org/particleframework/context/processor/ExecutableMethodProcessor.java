/*
 * Copyright 2017 original authors
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
package org.particleframework.context.processor;

import org.particleframework.context.ApplicationContext;
import org.particleframework.context.BeanContext;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.inject.annotation.Executable;

import java.lang.annotation.Annotation;

/**
 * <p>A class capable of processing an {@link org.particleframework.inject.ExecutableMethod} instances</p>
 *
 * <p>The use case here is framework components that need to react to the presence of an annotation. For example given the following annotation:</p>
 *
 * <pre class="code">
 *  &#064;Executable
 *  &#064;Retention(RUNTIME)
 *  &#064;Target(ElementType.METHOD)
 *  public &#064;interface Scheduled {
 *     String cron()
 *  }
 * </pre>
 *
 * <p>One could write a {@code ExecutableMethodProcessor} that processed all methods annotated with {@literal @}Scheduled:</p>
 *
 * <pre class="code">
 * {@code
 * public class MyProcessor implements ExecutableMethodProcessor<Scheduled> {
 *
 * }}
 *
 * </pre>
 *
 * @author Graeme Rocher
 * @since 1.0
 *
 * @param <A> The annotation type, which should be a stereotype of {@link Executable}
 */
public interface ExecutableMethodProcessor<A extends Annotation> extends AnnotationProcessor<A, ExecutableMethod<Object,Object>> {

    /**
     * The process method will be called for every {@link ExecutableMethod} that is annotated with the type parameter A
     *
     * @param method The executable method
     */
    @Override
    void process(ExecutableMethod<Object,Object> method);
}
