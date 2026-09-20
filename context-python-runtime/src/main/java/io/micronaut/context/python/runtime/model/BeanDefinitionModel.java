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

import java.util.List;
import java.util.Map;

/**
 * Everything a bean definition class is generated from.
 *
 * @param definitionClassName The name of the generated definition class
 * @param constructor         The constructor
 * @param methods             The injected and lifecycle methods, in invocation order
 * @param executableMethods   The executable methods, in dispatch order
 * @param info                The precalculated info
 * @param exposedTypes        The exposed type names: the declared ones, or the bean type and its accessible super types
 * @param exposedTypesDeclared Whether the exposed types were declared ({@code @Bean(typed = ...)}), which narrows candidate selection
 * @param typeArguments       The type arguments by generic super type or interface
 * @since 5.3.0
 */
public record BeanDefinitionModel(String definitionClassName, ConstructorModel constructor, List<InjectedMethodModel> methods,
                                  List<ExecutableMethodModel> executableMethods,
                                  PrecalculatedInfoModel info, List<String> exposedTypes, boolean exposedTypesDeclared,
                                  Map<String, List<ArgumentModel>> typeArguments) {
}
