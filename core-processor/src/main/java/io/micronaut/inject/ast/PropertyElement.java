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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;

import java.util.Optional;

/**
 * A property element represents a JavaBean property on a {@link ClassElement}.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PropertyElement extends TypedElement, MemberElement {

    /**
     * @return The type of the property
     */
    @NonNull
    @Override
    ClassElement getType();

    /**
     * Return true the property is excluded.
     *
     * @return True if the property is excluded
     * @since 4.0.0
     */
    default boolean isExcluded() {
        return false;
    }

    /**
     * Return true only if the property has a getter but no setter.
     *
     * @return True if the property is read only.
     */
    default boolean isReadOnly() {
        return getWriteMember().isEmpty();
    }

    /**
     * Return true only if the property doesn't support modifying the value.
     *
     * @return True if the property is writing only.
     * @since 4.0.0
     */
    default boolean isWriteOnly() {
        return getReadMember().isEmpty();
    }

    /**
     * The field representing the property.
     * NOTE: The field can be returned even if getter/setter are present.
     *
     * @return The field
     * @since 4.0.0
     */
    default Optional<FieldElement> getField() {
        return Optional.empty();
    }

    /**
     * @return The method to write the property
     * @since 4.0.0
     */
    default Optional<MethodElement> getWriteMethod() {
        return Optional.empty();
    }

    /**
     * @return The method to read the property
     */
    default Optional<MethodElement> getReadMethod() {
        return Optional.empty();
    }

    /**
     * @return The member to read the property
     * @since 4.0.0
     */
    default Optional<? extends MemberElement> getReadMember() {
        if (getReadAccessKind() == AccessKind.METHOD) {
            return getReadMethod();
        }
        return getField();
    }

    /**
     * @return The read type.
     * @since 4.4.0
     */
    default Optional<ClassElement> getReadType() {
        if (getReadAccessKind() == AccessKind.METHOD) {
            return getReadMethod().map(MethodElement::getGenericReturnType);
        }
        return getField().map(TypedElement::getGenericType);
    }

    /**
     * @return The member to write the property
     * @since 4.0.0
     */
    default Optional<? extends MemberElement> getWriteMember() {
        if (getWriteAccessKind() == AccessKind.METHOD) {
            return getWriteMethod();
        }
        return getField().filter(fieldElement -> !fieldElement.isFinal());
    }

    /**
     * @return The write type.
     * @since 4.4.0
     */
    default Optional<ClassElement> getWriteType() {
        if (getWriteAccessKind() == AccessKind.METHOD) {
            return getWriteMethod().flatMap(methodElement -> {
                if (methodElement.getParameters().length > 0) {
                    return Optional.of(methodElement.getParameters()[0].getGenericType());
                }
                return Optional.empty();
            });
        }
        return getField().filter(fieldElement -> !fieldElement.isFinal()).map(TypedElement::getGenericType);
    }

    /**
     * @return The read type annotation metadata.
     * @since 4.4.0
     */
    default Optional<AnnotationMetadata> getReadTypeAnnotationMetadata() {
        return Optional.empty();
    }

    /**
     * @return The write type annotation metadata.
     * @since 4.4.0
     */
    default Optional<AnnotationMetadata> getWriteTypeAnnotationMetadata() {
        return Optional.empty();
    }

    /**
     * @return The read access kind of the property
     * @since 4.0.0
     */
    default AccessKind getReadAccessKind() {
        return AccessKind.METHOD;
    }

    /**
     * @return The write access kind of the property
     * @since 4.0.0
     */
    default AccessKind getWriteAccessKind() {
        return AccessKind.METHOD;
    }

    /**
     * Does this property override the given property. Supported only with languages that have native properties.
     * @param overridden The overridden method.
     * @return True this property overrides the given property.
     * @since 4.0.0
     */
    default boolean overrides(PropertyElement overridden) {
        return false;
    }

    @Override
    default Optional<String> getDocumentation(boolean parse) {
        if (!parse) {
            return Optional.empty();
        }
        try {
            Optional<FieldElement> field = getField();
            if (field.isPresent()) {
                String javadoc = field.get().getDocumentation(parse).orElse(null);
                if (javadoc != null) {
                    return Optional.of(javadoc);
                }
            }
            Optional<MethodElement> readMethod = getReadMethod();
            if (readMethod.isPresent()) {
                MethodElement methodElement = readMethod.get();
                Optional<String> returnDoc = methodElement.getReturnType().getDocumentation(parse);
                if (returnDoc.isPresent()) {
                    return returnDoc;
                }
                Optional<String> methodDoc = methodElement.getDocumentation(parse);
                if (methodDoc.isPresent()) {
                    return methodDoc;
                }
            }
            Optional<MethodElement> writeMethod = getWriteMethod();
            if (writeMethod.isPresent()) {
                MethodElement methodElement = writeMethod.get();
                if (methodElement.getParameters().length > 0) {
                    ParameterElement parameter = methodElement.getParameters()[0];
                    Optional<String> paramDoc = parameter.getDocumentation(parse);
                    if (paramDoc.isPresent()) {
                        return paramDoc;
                    }
                }
                Optional<String> methodDoc = methodElement.getDocumentation(parse);
                if (methodDoc.isPresent()) {
                    return methodDoc;
                }
            }
        } catch (Exception ignore) {
            // ignore
        }
        return Optional.empty();
    }

    /**
     * The access type for bean properties.
     * @since 4.0.0
     */
    enum AccessKind {
        /**
         * Allows the use of public or package-protected fields to represent bean properties.
         */
        FIELD,
        /**
         * The default behaviour which is to favour public getters for bean properties.
         */
        METHOD
    }

}
