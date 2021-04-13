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
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.annotation.processing.visitor.JavaClassElement;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Implementation of {@link ConfigurationMetadataBuilder} for Java.
 *
 * @author Graeme Rocher
 * @see ConfigurationMetadataBuilder
 * @since 1.0
 */
public class JavaConfigurationMetadataBuilder extends ConfigurationMetadataBuilder<TypeElement> {

    private final AnnotationUtils annotationUtils;
    private final ModelUtils modelUtils;
    private final Elements elements;
    private final Map<String, ClassElement> originatingElements = new LinkedHashMap<>();

    /**
     * @param elements The {@link Elements}
     * @param types    The {@link Types}
     * @param annotationUtils The annotation utils
     */
    public JavaConfigurationMetadataBuilder(Elements elements, Types types, AnnotationUtils annotationUtils) {
        this.elements = elements;
        this.annotationUtils = annotationUtils;
        this.modelUtils = new ModelUtils(elements, types);
        // ensure initialization
        final TypeElement crte = elements.getTypeElement(ConfigurationReader.class.getName());
        if (crte != null) {
            getAnnotationMetadata(crte);
        }
        final TypeElement epte = elements.getTypeElement(EachProperty.class.getName());
        if (epte != null) {
            getAnnotationMetadata(epte);
        }
    }

    /**
     * @return The {@link Elements}
     */
    public Elements getElements() {
        return elements;
    }

    @NonNull
    @Override
    public io.micronaut.inject.ast.Element[] getOriginatingElements() {
        return originatingElements.values().toArray(io.micronaut.inject.ast.Element.EMPTY_ELEMENT_ARRAY);
    }

    @Override
    protected String buildPropertyPath(TypeElement owningType, TypeElement declaringType, String propertyName) {
        addOriginatingElements(owningType);
        String value = buildTypePath(owningType, declaringType);
        return value + '.' + propertyName;
    }

    @Override
    protected String buildTypePath(TypeElement owningType, TypeElement declaringType, AnnotationMetadata annotationMetadata) {
        addOriginatingElements(owningType, declaringType);
        String initialPath = calculateInitialPath(owningType, annotationMetadata);
        StringBuilder path = new StringBuilder(initialPath);

        prependSuperclasses(declaringType, path);
        if (owningType.getNestingKind() == NestingKind.MEMBER) {
            // we have an inner class, so prepend inner class
            Element enclosingElement = owningType.getEnclosingElement();
            if (enclosingElement instanceof TypeElement) {
                TypeElement enclosingType = (TypeElement) enclosingElement;
                while (true) {
                    AnnotationMetadata enclosingTypeMetadata = getAnnotationMetadata(enclosingType);
                    Optional<String> parentConfig = enclosingTypeMetadata.getValue(ConfigurationReader.class, String.class);
                    if (parentConfig.isPresent()) {
                        String parentPath = pathEvaluationFunctionForMetadata(enclosingTypeMetadata).apply(parentConfig.get());
                        if (StringUtils.isNotEmpty(parentPath)) {
                            path.insert(0, parentPath + '.');
                        }
                        prependSuperclasses(enclosingType, path);
                        if (enclosingType.getNestingKind() == NestingKind.MEMBER) {
                            Element el = enclosingType.getEnclosingElement();
                            if (el instanceof TypeElement) {
                                enclosingType = (TypeElement) el;
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    } else {
                        String parentPath = pathEvaluationFunctionForMetadata(enclosingTypeMetadata).apply("");
                        if (StringUtils.isNotEmpty(parentPath)) {
                            path.insert(0, parentPath + '.');
                        }
                        prependSuperclasses(enclosingType, path);
                        if (enclosingType.getNestingKind() == NestingKind.MEMBER) {
                            Element el = enclosingType.getEnclosingElement();
                            if (el instanceof TypeElement) {
                                enclosingType = (TypeElement) el;
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        return path.toString();
    }

    @Override
    protected String buildTypePath(TypeElement owningType, TypeElement declaringType) {
        addOriginatingElements(owningType, declaringType);
        AnnotationMetadata annotationMetadata = getAnnotationMetadata(declaringType);
        return buildTypePath(owningType, declaringType, annotationMetadata);
    }

    private void addOriginatingElements(TypeElement... types) {
        for (TypeElement type : types) {
            String className = type.getQualifiedName().toString();
            if (!originatingElements.containsKey(className)) {
                originatingElements.put(className, new JavaClassElement(
                        type,
                        AnnotationMetadata.EMPTY_METADATA,
                        null
                ));
            }
        }
    }

    private String calculateInitialPath(TypeElement owningType, AnnotationMetadata annotationMetadata) {

        Function<String, String> evaluatePathFunction = pathEvaluationFunctionForMetadata(annotationMetadata);
        return annotationMetadata.getValue(ConfigurationReader.class, String.class)
            .map(evaluatePathFunction)
            .orElseGet(() -> {
                    AnnotationMetadata ownerMetadata = getAnnotationMetadata(owningType);
                    return ownerMetadata
                        .getValue(ConfigurationReader.class, String.class)
                        .map(pathEvaluationFunctionForMetadata(ownerMetadata))
                        .orElseGet(() ->
                            pathEvaluationFunctionForMetadata(annotationMetadata).apply("")
                        );
                }

            );
    }

    private Function<String, String> pathEvaluationFunctionForMetadata(AnnotationMetadata annotationMetadata) {
        return path -> {
            if (annotationMetadata.hasDeclaredAnnotation(EachProperty.class)) {
                if (annotationMetadata.booleanValue(EachProperty.class, "list").orElse(false)) {
                    return path + "[*]";
                } else {
                    return path + ".*";
                }
            }
            String prefix = annotationMetadata.getValue(ConfigurationReader.class, "prefix", String.class)
                .orElse(null);
            if (StringUtils.isNotEmpty(prefix)) {
                if (StringUtils.isEmpty(path)) {
                    return prefix;
                } else {
                    return prefix + "." + path;
                }
            } else {
                return path;
            }
        };
    }

    @Override
    protected String getTypeString(TypeElement type) {
        return modelUtils.resolveTypeReference(type.asType()).toString();
    }

    @Override
    protected AnnotationMetadata getAnnotationMetadata(TypeElement type) {
        return annotationUtils.getDeclaredAnnotationMetadata(type);
    }

    private void prependSuperclasses(TypeElement declaringType, StringBuilder path) {
        if (declaringType.getKind() == ElementKind.INTERFACE) {
            DeclaredType superInterface = resolveSuperInterface(declaringType);
            while (superInterface != null) {
                final TypeElement element = (TypeElement) superInterface.asElement();
                AnnotationMetadata annotationMetadata = annotationUtils.getDeclaredAnnotationMetadata(element);
                String parentConfig = annotationMetadata.getValue(ConfigurationReader.class, String.class).orElse("");
                String parentPath = pathEvaluationFunctionForMetadata(annotationMetadata).apply(parentConfig);
                if (StringUtils.isNotEmpty(parentPath)) {
                    path.insert(0, parentPath + '.');
                }
                superInterface = resolveSuperInterface(element);
            }
        } else {
            TypeMirror superclass = declaringType.getSuperclass();
            while (superclass instanceof DeclaredType) {
                DeclaredType declaredType = (DeclaredType) superclass;
                Element element = declaredType.asElement();
                AnnotationMetadata annotationMetadata = annotationUtils.getDeclaredAnnotationMetadata(element);
                Optional<String> parentConfig = annotationMetadata.getValue(ConfigurationReader.class, String.class);
                if (parentConfig.isPresent()) {
                    String parentPath = pathEvaluationFunctionForMetadata(annotationMetadata).apply(parentConfig.get());
                    if (StringUtils.isNotEmpty(parentPath)) {
                        path.insert(0, parentPath + '.');
                    }
                    superclass = ((TypeElement) element).getSuperclass();
                } else {
                    if (annotationMetadata.isPresent(ConfigurationReader.class, "prefix")) {
                        String parentPath = pathEvaluationFunctionForMetadata(annotationMetadata).apply("");
                        if (StringUtils.isNotEmpty(parentPath)) {
                            path.insert(0, parentPath + '.');
                        }
                        superclass = ((TypeElement) element).getSuperclass();
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private DeclaredType resolveSuperInterface(TypeElement declaringType) {
        return declaringType.getInterfaces().stream().filter(tm ->
            tm instanceof DeclaredType &&
                    annotationUtils.getAnnotationMetadata(((DeclaredType) tm).asElement()).hasStereotype(ConfigurationReader.class)
        ).map(dt -> (DeclaredType) dt).findFirst().orElse(null);
    }

}
