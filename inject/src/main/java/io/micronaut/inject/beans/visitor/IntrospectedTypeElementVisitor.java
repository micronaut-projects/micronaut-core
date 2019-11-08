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
import io.micronaut.inject.ast.*;
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

    /**
     * The position of the visitor.
     */
    public static final int POSITION = -100;

    private static final String JAVAX_VALIDATION_CONSTRAINT = "javax.validation.Constraint";
    private static final AnnotationValue<Introspected.IndexedAnnotation> ANN_CONSTRAINT = AnnotationValue.builder(Introspected.IndexedAnnotation.class)
            .member("annotation", new AnnotationClassValue<>(JAVAX_VALIDATION_CONSTRAINT))
            .build();
    private static final String JAVAX_VALIDATION_VALID = "javax.validation.Valid";
    private static final AnnotationValue<Introspected.IndexedAnnotation> ANN_VALID = AnnotationValue.builder(Introspected.IndexedAnnotation.class)
            .member("annotation", new AnnotationClassValue<>(JAVAX_VALIDATION_VALID))
            .build();

    private Map<String, BeanIntrospectionWriter> writers = new LinkedHashMap<>(10);

    @Override
    public int getOrder() {
        // lower precedence, all others to mutate metadata as necessary
        return POSITION;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        final AnnotationValue<Introspected> introspected = element.getAnnotation(Introspected.class);
        if (introspected != null && !writers.containsKey(element.getName()) && !element.isAbstract()) {

            final String[] packages = introspected.get("packages", String[].class, StringUtils.EMPTY_STRING_ARRAY);
            final AnnotationClassValue[] classes = introspected.get("classes", AnnotationClassValue[].class, new AnnotationClassValue[0]);
            final boolean metadata = introspected.get("annotationMetadata", boolean.class, true);

            final Set<String> includes = CollectionUtils.setOf(introspected.get("includes", String[].class, StringUtils.EMPTY_STRING_ARRAY));
            final Set<String> excludes = CollectionUtils.setOf(introspected.get("excludes", String[].class, StringUtils.EMPTY_STRING_ARRAY));
            final Set<String> excludedAnnotations = CollectionUtils.setOf(introspected.get("excludedAnnotations", String[].class, StringUtils.EMPTY_STRING_ARRAY));
            final Set<String> includedAnnotations = CollectionUtils.setOf(introspected.get("includedAnnotations", String[].class, StringUtils.EMPTY_STRING_ARRAY));
            final Set<AnnotationValue> indexedAnnotations;

            final Set<AnnotationValue> toIndex = CollectionUtils.setOf(introspected.get("indexed", AnnotationValue[].class, new AnnotationValue[0]));

            if (CollectionUtils.isEmpty(toIndex)) {
                indexedAnnotations = CollectionUtils.setOf(
                        ANN_CONSTRAINT,
                        ANN_VALID
                );
            } else {
                toIndex.addAll(
                    CollectionUtils.setOf(
                            ANN_CONSTRAINT,
                            ANN_VALID
                    )
                );
                indexedAnnotations = toIndex;
            }

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

                            processElement(context, metadata, includes, excludes, excludedAnnotations, indexedAnnotations, ce, writer);

                        }
                    });
                }
            } else if (ArrayUtils.isNotEmpty(packages)) {

                if (includedAnnotations.isEmpty()) {
                    context.fail("When specifying 'packages' you must also specify 'includedAnnotations' to limit scanning", element);
                } else {
                    for (String aPackage : packages) {
                        ClassElement[] elements = context.getClassElements(aPackage, includedAnnotations.toArray(new String[0]));
                        int j = 0;
                        for (ClassElement classElement : elements) {
                            if (classElement.isAbstract() || !classElement.isPublic()) {
                                continue;
                            }
                            final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                                    element.getName(),
                                    j++,
                                    classElement.getName(),
                                    metadata ? element.getAnnotationMetadata() : null
                            );

                            processElement(context, metadata, includes, excludes, excludedAnnotations, indexedAnnotations, classElement, writer);
                        }
                    }
                }
            } else {

                final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                        element.getName(),
                        metadata ? element.getAnnotationMetadata() : null
                );

                processElement(context, metadata, includes, excludes, excludedAnnotations, indexedAnnotations, element, writer);
            }

        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {

        for (BeanIntrospectionWriter writer : writers.values()) {
            try {
                writer.accept(visitorContext);
            } catch (IOException e) {
                throw new ClassGenerationException("I/O error occurred during class generation: " + e.getMessage(), e);
            }
        }
    }

    private void processElement(VisitorContext context, boolean metadata, Set<String> includes, Set<String> excludes, Set<String> excludedAnnotations, Set<AnnotationValue> indexedAnnotations, ClassElement ce, BeanIntrospectionWriter writer) {
        final List<PropertyElement> beanProperties = ce.getBeanProperties();
        Optional<MethodElement> constructorElement = ce.getPrimaryConstructor();

        if (!constructorElement.isPresent()) {
            context.fail("Introspected types must have a single public constructor or static @Creator method", ce);
        } else {
            final MethodElement constructor = constructorElement.get();
            if (Arrays.stream(constructor.getParameters()).anyMatch(p -> p.getType() == null)) {
                context.fail("Introspected constructor includes unsupported argument types", ce);
            } else {
                process(constructor, ce.getDefaultConstructor().orElse(null), writer, beanProperties, includes, excludes, excludedAnnotations, indexedAnnotations, metadata);
            }
        }
    }

    private void process(
            MethodElement constructorElement,
            MethodElement defaultConstructor,
            BeanIntrospectionWriter writer,
            List<PropertyElement> beanProperties,
            Set<String> includes,
            Set<String> excludes,
            Set<String> ignored,
            Set<AnnotationValue> indexedAnnotations,
            boolean metadata) {

        final ParameterElement[] parameters = constructorElement.getParameters();
        if (ArrayUtils.isNotEmpty(parameters)) {
            writer.visitConstructor(constructorElement);
        }
        if (defaultConstructor != null) {
            writer.visitDefaultConstructor(defaultConstructor);
        }

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
                        beanProperty.getReadMethod().orElse(null),
                        beanProperty.getWriteMethod().orElse(null),
                        beanProperty.isReadOnly(),
                        metadata ? beanProperty.getAnnotationMetadata() : null,
                        beanProperty.getType().getTypeArguments()
                );

                for (AnnotationValue<?> indexedAnnotation : indexedAnnotations) {
                    indexedAnnotation.get("annotation", String.class).ifPresent(annotationName -> {
                        if (beanProperty.hasStereotype(annotationName)) {
                            writer.indexProperty(
                                    new AnnotationValue<>(annotationName),
                                    name,
                                    indexedAnnotation.get("member", String.class)
                                            .flatMap(m -> beanProperty.getValue(annotationName, m, String.class)).orElse(null)
                            );
                        }
                    });

                }
            }
        }
        writers.put(writer.getBeanType().getClassName(), writer);
    }

}
