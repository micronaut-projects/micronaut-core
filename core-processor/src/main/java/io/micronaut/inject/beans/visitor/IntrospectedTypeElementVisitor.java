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
package io.micronaut.inject.beans.visitor;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassGenerationException;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A {@link TypeElementVisitor} that visits classes annotated with {@link Introspected} and produces
 * {@link io.micronaut.core.beans.BeanIntrospectionReference} instances at compilation time.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
public class IntrospectedTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    /**
     * The position of the visitor.
     */
    public static final int POSITION = -100;

    private final Map<String, BeanIntrospectionWriter> writers = new LinkedHashMap<>(10);

    @Override
    public int getOrder() {
        // lower precedence, all others to mutate metadata as necessary
        return POSITION;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasStereotype(Introspected.class)) {
            final AnnotationValue<Introspected> introspected = element.getAnnotation(Introspected.class);
            if (introspected != null && !writers.containsKey(element.getName())) {
                processIntrospected(element, context, introspected);
            }
        }
    }

    private boolean isIntrospected(VisitorContext context, ClassElement c) {
        return writers.containsKey(c.getName()) || context.getClassElement(c.getPackageName() + ".$" + c.getSimpleName() + "$Introspection").isPresent();
    }

    private void processIntrospected(ClassElement element, VisitorContext context, AnnotationValue<Introspected> introspected) {
        final String[] packages = introspected.stringValues("packages");
        final List<String> classes = Stream.concat(
            Arrays.stream(introspected.annotationClassValues("classes")).map(AnnotationClassValue::getName),
            Arrays.stream(introspected.stringValues("classNames"))
        ).toList();
        final boolean metadata = introspected.booleanValue("annotationMetadata").orElse(true);
        final Set<String> includedAnnotations = CollectionUtils.setOf(introspected.stringValues("includedAnnotations"));
        final Set<AnnotationValue<Annotation>> indexedAnnotations = CollectionUtils.setOf(introspected.get("indexed", AnnotationValue[].class, new AnnotationValue[0]));
        final String targetPackage = introspected.stringValue("targetPackage").orElse(element.getPackageName());

        if (!classes.isEmpty()) {
            AtomicInteger index = new AtomicInteger(0);
            classes.stream().flatMap(className -> context.getClassElement(className).stream()).forEach(ce -> {
                if (isIntrospected(context, ce)) {
                    return;
                }
                final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                    targetPackage,
                    element.getName(),
                    index.getAndIncrement(),
                    element,
                    ce,
                    metadata ? ce.getAnnotationMetadata() : null,
                    context
                );

                processElement(
                    metadata,
                    indexedAnnotations,
                    getExternalPropertyElementQuery(element, ce),
                    ce,
                    writer
                );
            });
        } else if (ArrayUtils.isNotEmpty(packages)) {
            if (includedAnnotations.isEmpty()) {
                context.fail("When specifying 'packages' you must also specify 'includedAnnotations' to limit scanning", element);
            } else {
                for (String aPackage : packages) {
                    ClassElement[] elements = context.getClassElements(aPackage, includedAnnotations.toArray(new String[0]));
                    int j = 0;
                    for (ClassElement classElement : elements) {
                        if (classElement.isAbstract() || !classElement.isPublic() || isIntrospected(context, classElement)) {
                            continue;
                        }
                        final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                            targetPackage,
                            element.getName(),
                            j++,
                            element,
                            classElement,
                            metadata ? element.getAnnotationMetadata() : null,
                            context
                        );

                        processElement(metadata,
                            indexedAnnotations,
                            getExternalPropertyElementQuery(element, classElement),
                            classElement,
                            writer);
                    }
                }
            }
        } else {
            final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                targetPackage,
                element,
                metadata ? element.getAnnotationMetadata() : null,
                context
            );
            processElement(metadata, indexedAnnotations, element, writer);
        }
    }

    @NonNull
    private static PropertyElementQuery getExternalPropertyElementQuery(ClassElement defined, ClassElement current) {
        AnnotationMetadataHierarchy hierarchy = new AnnotationMetadataHierarchy(defined, current);
        return PropertyElementQuery.of(hierarchy).ignoreSettersWithDifferingType(true);
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        try {
            if (!writers.isEmpty()) {
                for (BeanIntrospectionWriter writer : writers.values()) {
                    try {
                        writer.accept(visitorContext);
                    } catch (IOException e) {
                        throw new ClassGenerationException("I/O error occurred during class generation: " + e.getMessage(), e);
                    }
                }
            }
        } finally {
            writers.clear();
        }
    }

    private void processElement(boolean metadata,
                                Set<AnnotationValue<Annotation>> indexedAnnotations,
                                ClassElement ce,
                                BeanIntrospectionWriter writer) {
        processElement(metadata,
            indexedAnnotations,
            PropertyElementQuery.of(ce).ignoreSettersWithDifferingType(true),
            ce,
            writer);
    }

    private void processElement(boolean metadata,
                                Set<AnnotationValue<Annotation>> indexedAnnotations,
                                PropertyElementQuery propertyElementQuery,
                                ClassElement ce,
                                BeanIntrospectionWriter writer) {
        List<PropertyElement> beanProperties = ce.getBeanProperties(propertyElementQuery).stream()
            .filter(p -> !p.isExcluded())
            .toList();
        Optional<MethodElement> constructorElement = ce.getPrimaryConstructor();
        constructorElement.ifPresent(constructorEl -> {
            if (ArrayUtils.isNotEmpty(constructorEl.getParameters())) {
                writer.visitConstructor(constructorEl);
            }
        });
        ce.getDefaultConstructor().ifPresent(writer::visitDefaultConstructor);

        for (PropertyElement beanProperty : beanProperties) {
            if (beanProperty.isExcluded()) {
                continue;
            }
            AnnotationMetadata annotationMetadata;
            if (metadata) {
                annotationMetadata = beanProperty.getTargetAnnotationMetadata();
                if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
                    annotationMetadata = ((AnnotationMetadataHierarchy) annotationMetadata).merge();
                }
            } else {
                annotationMetadata = null;
            }

            writer.visitProperty(
                beanProperty.getType(),
                beanProperty.getGenericType(),
                beanProperty.getName(),
                beanProperty.getReadMember().orElse(null),
                beanProperty.getWriteMember().orElse(null),
                beanProperty.isReadOnly(),
                annotationMetadata,
                beanProperty.getGenericType().getTypeArguments()
            );

            for (AnnotationValue<?> indexedAnnotation : indexedAnnotations) {
                indexedAnnotation.get("annotation", String.class).ifPresent(annotationName -> {
                    if (beanProperty.hasStereotype(annotationName)) {
                        writer.indexProperty(
                            annotationName,
                            beanProperty.getName(),
                            indexedAnnotation.get("member", String.class)
                                .flatMap(m1 -> beanProperty.getValue(annotationName, m1, String.class)).orElse(null)
                        );
                    }
                });
            }
        }

        writers.put(writer.getBeanType().getClassName(), writer);

        addExecutableMethods(ce, writer, beanProperties);
    }

    private void addExecutableMethods(ClassElement ce, BeanIntrospectionWriter writer, List<PropertyElement> beanProperties) {
        Set<MethodElement> added = new HashSet<>();
        for (PropertyElement beanProperty : beanProperties) {
            if (beanProperty.isExcluded()) {
                continue;
            }
            beanProperty.getReadMethod().filter(m -> m.hasStereotype(Executable.class) && !m.isAbstract()).ifPresent(methodElement -> {
                added.add(methodElement);
                writer.visitBeanMethod(methodElement);
            });
            beanProperty.getWriteMethod().filter(m -> m.hasStereotype(Executable.class) && !m.isAbstract()).ifPresent(methodElement -> {
                added.add(methodElement);
                writer.visitBeanMethod(methodElement);
            });
        }
        ElementQuery<MethodElement> query = ElementQuery.of(MethodElement.class)
            .modifiers(modifiers -> !modifiers.contains(ElementModifier.STATIC))
            .annotated(am -> am.hasStereotype(Executable.class));
        List<MethodElement> executableMethods = ce.getEnclosedElements(query);
        for (MethodElement executableMethod : executableMethods) {
            if (added.contains(executableMethod)) {
                continue;
            }
            added.add(executableMethod);
            writer.visitBeanMethod(executableMethod);
        }
    }

}
