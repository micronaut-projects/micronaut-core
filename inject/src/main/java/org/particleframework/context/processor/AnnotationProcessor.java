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

import org.particleframework.inject.ExecutableMethod;

import java.lang.annotation.Annotation;

/**
 * <p>An annotation processor is an object that processes the presence if a given annotation.</p>
 *
 * <p>The {@link #process(Object)} method returns void since a processor is not able to mutate the object itself or return an alternative instance, instead the design of a processor is to react to the rep</p>
 *
 * @see ExecutableMethodProcessor
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationProcessor<A extends Annotation, T> {

    /**
     * The process method will be called for every {@link ExecutableMethod} that is annotated with the type parameter A
     *
     * @param object The object to be processed
     */
    void process(T object);
}
