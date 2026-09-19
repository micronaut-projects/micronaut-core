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
package io.micronaut.context.python.runtime.model;

import org.jspecify.annotations.Nullable;

/**
 * The resolved model of one Python class: its JVM wrapper, the annotation metadata after all visitors, and the
 * artifacts the compiler decided it needs. Bean definitions and introspections are independent: a class may have
 * either, both or none.
 *
 * @param className          The binary name of the JVM wrapper class
 * @param annotationMetadata The class annotation metadata
 * @param beanDefinition     The bean definition, if the class is a bean
 * @param introspection      The introspection, if the class is introspected
 * @since 5.3.0
 */
public record ClassModel(String className, AnnotationMetadataModel annotationMetadata,
                         @Nullable BeanDefinitionModel beanDefinition, @Nullable IntrospectionModel introspection) {
}
