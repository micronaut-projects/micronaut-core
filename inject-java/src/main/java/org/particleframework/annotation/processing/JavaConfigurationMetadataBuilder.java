/*
 * Copyright 2018 original authors
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
package org.particleframework.annotation.processing;

import org.particleframework.context.annotation.ConfigurationReader;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.inject.configuration.ConfigurationMetadataBuilder;

import javax.lang.model.element.Element;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of {@link ConfigurationMetadataBuilder} for Java
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JavaConfigurationMetadataBuilder extends ConfigurationMetadataBuilder<TypeElement> {
    private final AnnotationUtils annotationUtils;
    private final ModelUtils modelUtils;
    private final Elements elements;
    private final Map<String, String> typePaths = new HashMap<>();

    public JavaConfigurationMetadataBuilder(Elements elements, Types types) {
        this.elements = elements;
        this.annotationUtils = new AnnotationUtils(elements);
        this.modelUtils = new ModelUtils(elements, types);
    }

    public Elements getElements() {
        return elements;
    }

    @Override
    protected String buildPropertyPath(TypeElement declaringType, String propertyName) {
        String value = buildTypePath(declaringType);
        return value + '.' + propertyName;
    }

    @Override
    protected String buildTypePath(TypeElement declaringType) {
        return typePaths.computeIfAbsent(declaringType.getQualifiedName().toString(), s -> {
            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(declaringType);
            StringBuilder path = new StringBuilder(annotationMetadata.getValue(ConfigurationReader.class, String.class).orElseThrow(() ->
                    new IllegalStateException("@ConfigurationProperties found with no value for type: " + declaringType.getQualifiedName().toString())
            ));

            prependSuperclasses(declaringType, path);
            if( declaringType.getNestingKind() == NestingKind.MEMBER ) {
                // we have an inner class, so prepend inner class
                Element enclosingElement = declaringType.getEnclosingElement();
                if(enclosingElement instanceof TypeElement) {
                    TypeElement enclosingType = (TypeElement) enclosingElement;
                    while(true) {

                        Optional<String> parentConfig = annotationUtils.getAnnotationMetadata(enclosingType).getValue(ConfigurationReader.class, String.class);
                        if(parentConfig.isPresent()) {
                            path.insert(0, parentConfig.get() + '.');
                            prependSuperclasses(enclosingType, path);
                            if( enclosingType.getNestingKind() == NestingKind.MEMBER) {
                                Element el = enclosingType.getEnclosingElement();
                                if(el instanceof  TypeElement) {
                                    enclosingType = (TypeElement) el;
                                }
                                else {
                                    break;
                                }
                            }
                            else {
                                break;
                            }
                        }
                        else {
                            break;
                        }
                    }

                }

            }
            return path.toString();
        });

    }

    private void prependSuperclasses(TypeElement declaringType, StringBuilder path) {
        TypeMirror superclass = declaringType.getSuperclass();
        while(superclass instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) superclass;
            Element element = declaredType.asElement();
            Optional<String> parentConfig = annotationUtils.getAnnotationMetadata(element).getValue(ConfigurationReader.class, String.class);
            if(parentConfig.isPresent()) {
                path.insert(0, parentConfig.get() + '.');
                superclass = ((TypeElement)element).getSuperclass();
            }
            else {
                break;
            }
        }
    }

    @Override
    protected String getTypeString(TypeElement type) {
        return modelUtils.resolveTypeReference(type).toString();
    }
}
