/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassGenerationException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link TypeElementVisitor} that visits classes annotated with {@link Introspected} and produces
 * {@link io.micronaut.core.beans.BeanIntrospectionReference} instances at compilation time.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
public class IntrospectedTypeElementVisitor implements TypeElementVisitor<Introspected, Object> {

    private List<BeanIntrospectionWriter> writers = new ArrayList<>(10);

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        final AnnotationValue<Introspected> introspected = element.getAnnotation(Introspected.class);
        if (introspected != null) {

            final String[] packages = introspected.get("packages", String[].class, StringUtils.EMPTY_STRING_ARRAY);
            final AnnotationClassValue[] classes = introspected.get("classes", AnnotationClassValue[].class, new AnnotationClassValue[0]);
            final boolean metadata = introspected.get("annotationMetadata", boolean.class, true);

            final Set<String> includes = CollectionUtils.setOf(introspected.get("includes", String[].class, StringUtils.EMPTY_STRING_ARRAY));
            final Set<String> excludes = CollectionUtils.setOf(introspected.get("excludes", String[].class, StringUtils.EMPTY_STRING_ARRAY));
            final Set<String> excludedAnnotations = CollectionUtils.setOf(introspected.get("excludedAnnotations", String[].class, StringUtils.EMPTY_STRING_ARRAY));
            final Set<String> includedAnnotations = CollectionUtils.setOf(introspected.get("includedAnnotations", String[].class, StringUtils.EMPTY_STRING_ARRAY));

            if (ArrayUtils.isNotEmpty(classes)) {
                AtomicInteger index = new AtomicInteger(0);
                for (AnnotationClassValue aClass : classes) {
                    final Optional<ClassElement> classElement = context.getClassElement(aClass.getName());
                    classElement.ifPresent(ce -> {
                        if (!ce.isAbstract() && ce.isPublic()) {
                            final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                                    element.getName(),
                                    index.getAndIncrement(),
                                    ce.getName(),
                                    metadata ? element.getAnnotationMetadata() : null
                            );

                            final List<PropertyElement> beanProperties = ce.getBeanProperties();
                            process(writer, beanProperties, includes, excludes, excludedAnnotations, metadata);
                        }
                    });
                }
            } else if (ArrayUtils.isNotEmpty(packages)) {

                if (includedAnnotations.isEmpty()) {
                    context.fail("When specifying 'packages' you must also specify 'includedAnnotations' to limit scanning", element);
                } else {
                    for (String aPackage : packages) {
                        ClassElement[] elements = context.getClassElements(aPackage, includedAnnotations.toArray(new String[0]));
                        for (int i = 0; i < elements.length; i++) {
                            ClassElement classElement = elements[i];
                            if (classElement.isAbstract() || !classElement.isPublic()) {
                                continue;
                            }
                            final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                                    element.getName(),
                                    i,
                                    classElement.getName(),
                                    metadata ? element.getAnnotationMetadata() : null
                            );

                            final List<PropertyElement> beanProperties = classElement.getBeanProperties();
                            process(writer, beanProperties, includes, excludes, excludedAnnotations, metadata);
                        }
                    }
                }
            } else {

                final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                        element.getName(),
                        metadata ? element.getAnnotationMetadata() : null
                );

                final List<PropertyElement> beanProperties = element.getBeanProperties();
                process(writer, beanProperties, includes, excludes, excludedAnnotations, metadata);
            }

        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {

        for (BeanIntrospectionWriter writer : writers) {
            try {
                writer.accept(visitorContext);
            } catch (IOException e) {
                throw new ClassGenerationException("I/O error occurred during class generation: " + e.getMessage(), e);
            }
        }
    }

    private void process(
            BeanIntrospectionWriter writer,
            List<PropertyElement> beanProperties,
            Set<String> includes,
            Set<String> excludes,
            Set<String> ignored, boolean metadata) {
        for (PropertyElement beanProperty : beanProperties) {
            final ClassElement type = beanProperty.getType();
            if (type != null) {

                final String name = beanProperty.getName();
                if (!includes.isEmpty() && !includes.contains(name)) {
                    continue;
                }
                if (!excludes.isEmpty() && excludes.contains(name)) {
                    continue;
                }

                if (!ignored.isEmpty() && ignored.stream().anyMatch(beanProperty::hasAnnotation)) {
                    continue;
                }

                writer.visitProperty(
                        type,
                        name,
                        beanProperty.isReadOnly(),
                        metadata ? beanProperty.getAnnotationMetadata() : null,
                        beanProperty.getType().getTypeArguments()
                );
            }
        }
        writers.add(writer);
    }

}
