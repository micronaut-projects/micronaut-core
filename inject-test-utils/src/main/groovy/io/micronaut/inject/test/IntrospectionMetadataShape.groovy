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
package io.micronaut.inject.test

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanMethod
import io.micronaut.core.beans.BeanProperty

/**
 * Every answer a {@link BeanIntrospection} gives through the API that predates
 * {@link io.micronaut.core.annotation.Introspected#members()}, rendered as text so that two introspections can
 * be compared as a whole rather than assertion by assertion.
 *
 * <p>Written for the specs that check {@code members = true} is additive: the same type is compiled with and
 * without the members, and the two must answer the same.</p>
 */
class IntrospectionMetadataShape {

    /**
     * The metadata an introspection exposes: the introspection itself, the properties with their arguments,
     * the bean methods with their return types and arguments, and the constructor.
     *
     * @param introspection The introspection
     * @return The rendered metadata
     */
    static String of(BeanIntrospection<?> introspection) {
        List<String> lines = ["introspection:\n" + of(introspection.annotationMetadata)]
        for (BeanProperty property : introspection.beanProperties) {
            lines << "property ${property.name}:\n" + of(property.annotationMetadata)
            lines << "property ${property.name} declared:\n" + of(property.annotationMetadata.declaredMetadata)
            lines << "property ${property.name} argument:\n" + of(property.asArgument().annotationMetadata)
            property.asArgument().typeParameters.each {
                lines << "property ${property.name} type argument ${it.name}:\n" + of(it.annotationMetadata)
            }
        }
        for (BeanMethod method : introspection.beanMethods.toSorted { it.name }) {
            lines << "method ${method.name}:\n" + of(method.annotationMetadata)
            lines << "method ${method.name} declared:\n" + of(method.declaredMetadata)
            lines << "method ${method.name} return:\n" + of(method.returnType.annotationMetadata)
            lines << "method ${method.name} return argument:\n" + of(method.returnType.asArgument().annotationMetadata)
            method.arguments.each { lines << "method ${method.name} argument ${it.name}:\n" + of(it.annotationMetadata) }
        }
        lines << "constructor:\n" + of(introspection.constructor.annotationMetadata)
        introspection.constructorArguments.each {
            lines << "constructor argument ${it.name}:\n" + of(it.annotationMetadata)
        }
        return lines.join("\n")
    }

    /**
     * Every answer an annotation metadata gives: the names, the declared names, the stereotypes, and for each
     * annotation its values, its default values and the annotations it is a stereotype of.
     *
     * @param metadata The metadata
     * @return The rendered metadata
     */
    static String of(AnnotationMetadata metadata) {
        List<String> lines = []
        lines << "names=" + metadata.annotationNames.toSorted()
        lines << "declared=" + metadata.declaredAnnotationNames.toSorted()
        lines << "stereotypes=" + metadata.stereotypeAnnotationNames.toSorted()
        lines << "declaredStereotypes=" + metadata.declaredStereotypeAnnotationNames.toSorted()
        for (String name : metadata.annotationNames.toSorted()) {
            lines << name + " values=" + metadata.getAnnotationValuesByName(name)*.toString() +
                    " defaults=" + canonical(metadata.getDefaultValues(name)) +
                    " byStereotype=" + metadata.getAnnotationNamesByStereotype(name).toSorted()
        }
        return lines.join("\n")
    }

    private static Object canonical(Object value) {
        if (value instanceof Map) {
            return new TreeMap(value.collectEntries { k, v -> [(k.toString()): canonical(v)] })
        }
        if (value instanceof Object[]) {
            return value.toList().collect { canonical(it) }
        }
        return value instanceof AnnotationValue ? value.toString() : value
    }
}
