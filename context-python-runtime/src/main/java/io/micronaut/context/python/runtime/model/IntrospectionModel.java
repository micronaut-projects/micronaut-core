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

import java.util.List;

/**
 * Everything an introspection class is generated from.
 *
 * @param introspectionClassName        The name of the generated introspection class
 * @param constructorAnnotationMetadata The constructor annotation metadata
 * @param constructorArguments          The constructor arguments
 * @param properties                    The properties, in declaration order
 * @param indexes                       The property index entries, in recording order
 * @param methods                       The executable methods the introspection exposes, in declaration order
 * @param enumConstants                 The constants when the introspected type is an enum, in declaration order, or null when it is not
 * @since 5.3.0
 */
public record IntrospectionModel(String introspectionClassName, AnnotationMetadataModel constructorAnnotationMetadata,
                                 List<ArgumentModel> constructorArguments, List<PropertyModel> properties,
                                 List<PropertyIndexModel> indexes, List<BeanMethodModel> methods,
                                 @Nullable List<EnumConstantModel> enumConstants) {
}
