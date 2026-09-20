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

/**
 * The factory method producing a bean: the definition looks the factory bean up and invokes the method with the
 * arguments its constructor model resolves.
 *
 * @param factoryTypeName The binary name of the factory class
 * @param methodName      The method name
 * @param returnType      The declared return type of the method
 * @param isStatic        Whether the method is static, in which case no factory bean is looked up
 * @since 5.3.0
 */
public record FactoryMethodModel(String factoryTypeName, String methodName, ArgumentModel returnType, boolean isStatic) {
}
