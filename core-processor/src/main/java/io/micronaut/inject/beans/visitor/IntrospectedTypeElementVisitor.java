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
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
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
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassGenerationException;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

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
    private static final String ANN_LOMBOK_BUILDER = "lombok.Builder";

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
        boolean ignoreSettersWithDifferingType = introspected.booleanValue("ignoreSettersWithDifferingType").orElse(true);
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
                int introspectionIndex = index.getAndIncrement();
                processBuilderDefinition(ce, context, ce.findAnnotation(Introspected.class).orElse(introspected), introspectionIndex, targetPackage);
                final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                    targetPackage,
                    element.getName(),
                    introspectionIndex,
                    element,
                    ce,
                    metadata ? ce.getAnnotationMetadata() : null,
                    context
                );

                processElement(
                    metadata,
                    indexedAnnotations,
                    getExternalPropertyElementQuery(element, ce, ignoreSettersWithDifferingType),
                    ce,
                    writer
                );
            });
        } else if (ArrayUtils.isNotEmpty(packages)) {
            if (includedAnnotations.isEmpty()) {
                context.fail("When specifying 'packages' you must also specify 'includedAnnotations' to limit scanning", element);
            } else {
                for (String aPackage : packages) {
                    ClassElement[] elements = context.getClassElements(aPackage, includedAnnotations.toArray(EMPTY_STRING_ARRAY));
                    int j = 0;
                    for (ClassElement classElement : elements) {
                        if (classElement.isAbstract() || !classElement.isPublic() || isIntrospected(context, classElement)) {
                            continue;
                        }
                        int introspectionIndex = j++;
                        processBuilderDefinition(classElement, context, classElement.findAnnotation(Introspected.class).orElse(introspected), introspectionIndex, targetPackage);
                        final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                            targetPackage,
                            element.getName(),
                            introspectionIndex,
                            element,
                            classElement,
                            metadata ? classElement.getAnnotationMetadata() : null,
                            context
                        );


                        processElement(metadata,
                            indexedAnnotations,
                            getExternalPropertyElementQuery(element, classElement, ignoreSettersWithDifferingType),
                            classElement,
                            writer);
                    }
                }
            }
        } else {
            processBuilderDefinition(element, context, introspected, 0, targetPackage);
            final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                targetPackage,
                element,
                metadata ? element.getAnnotationMetadata() : null,
                context
            );
            processElement(metadata, indexedAnnotations, element, writer, ignoreSettersWithDifferingType);
        }
    }

    private void processBuilderDefinition(ClassElement element, VisitorContext context, AnnotationValue<Introspected> introspected, int index, String targetPackage) {
        AnnotationValue<Introspected.IntrospectionBuilder> builder = introspected.getAnnotation("builder", Introspected.IntrospectionBuilder.class).orElse(null);
        if (builder != null) {
            String builderMethod = builder.stringValue("builderMethod").orElse(null);
            String creatorMethod = builder.stringValue("creatorMethod").orElse(null);
            AnnotationClassValue<?> builderClass = builder.annotationClassValue("builderClass").orElse(null);
            String[] writePrefixes = builder.getAnnotation("accessorStyle", AccessorsStyle.class)
                .map(a -> a.stringValues("writePrefixes")).orElse(new String[]{""});
            processBuilderDefinition(
                element,
                context,
                introspected,
                index,
                targetPackage,
                builderMethod,
                creatorMethod,
                writePrefixes,
                builderClass
            );
        } else if (element.hasDeclaredAnnotation(ANN_LOMBOK_BUILDER)) {
            AnnotationValue<Annotation> lombokBuilder = element.getAnnotation(ANN_LOMBOK_BUILDER);
            String builderMethod = lombokBuilder.stringValue("builderMethodName").orElse("builder");
            MethodElement methodElement = element
                .getEnclosedElement(ElementQuery.ALL_METHODS.onlyStatic()
                    .filter(m -> m.getName().equals(builderMethod) && !m.getGenericReturnType().isVoid())
                    .onlyAccessible(element))
                .orElse(null);
            if (methodElement == null) {
                // Lombok processing not done yet, try again in the next round.
                throw new ElementPostponedToNextRoundException(element);
            }
            String creatorMethod = lombokBuilder.stringValue("buildMethodName").orElse("build");
            String[] writePrefixes = lombokBuilder.stringValue("setterPrefix").map(sp -> new String[]{sp}).orElse(new String[]{""});
            processBuilderDefinition(
                element,
                context,
                introspected,
                index,
                targetPackage,
                builderMethod,
                creatorMethod,
                writePrefixes,
                null
            );
        }
    }

    private void processBuilderDefinition(ClassElement element, VisitorContext context, AnnotationValue<Introspected> introspected, int index, String targetPackage, String builderMethod, String creatorMethod, String[] writePrefixes, AnnotationClassValue<?> builderClass) {
        if (builderMethod != null) {
            MethodElement methodElement = element
                .getEnclosedElement(ElementQuery.ALL_METHODS.onlyStatic()
                    .filter(m -> m.getName().equals(builderMethod) && !m.getGenericReturnType().isVoid())
                    .onlyAccessible(element))
                .orElse(null);
            if (methodElement != null) {
                ClassElement returnType = methodElement.getGenericReturnType();
                if (returnType.isPublic() || returnType.getPackageName().equals(element.getPackageName())) {
                    AnnotationValueBuilder<Introspected> replaceIntrospected = AnnotationValue.builder(introspected, RetentionPolicy.RUNTIME);
                    replaceIntrospected.member("builderClass", new AnnotationClassValue<>(returnType.getName()));
                    element.annotate(replaceIntrospected.build());
                    AnnotationMetadata methodMetadata = methodElement.getMethodAnnotationMetadata().getTargetAnnotationMetadata();

                    handleBuilder(
                        element,
                        context,
                        creatorMethod,
                        writePrefixes,
                        methodElement,
                        returnType.getDefaultConstructor().orElse(null),
                        returnType,
                        methodMetadata,
                        index,
                        targetPackage
                    );
                } else {
                    context.fail("Builder return type is not public. The method must be static and accessible.", methodElement);
                }
            } else {
                context.fail("Method " + builderMethod + "() specified by builderMethod not found. The method must be static and accessible.", element);
            }
        } else if (builderClass != null) {
            ClassElement builderClassElement = context.getClassElement(builderClass.getName()).orElse(null);
            if (builderClassElement != null) {
                AnnotationValueBuilder<Introspected> replaceIntrospected = AnnotationValue.builder(introspected, RetentionPolicy.RUNTIME);
                replaceIntrospected.member("builderClass", new AnnotationClassValue<>(builderClassElement.getName()));
                element.annotate(replaceIntrospected.build());

                handleBuilder(
                    element,
                    context,
                    creatorMethod,
                    writePrefixes,
                    builderClassElement.getPrimaryConstructor().orElse(null),
                    builderClassElement.getDefaultConstructor().orElse(null),
                    builderClassElement,
                    builderClassElement.getTargetAnnotationMetadata(),
                    index,
                    targetPackage);
            } else {
                context.fail("Builder class not found on compilation classpath: " + builderClass.getName(), element);
            }
        } else {
            context.fail("When specifying the 'builder' member of @Introspected you must supply either a builderClass or builderMethod", element);
        }
    }

    @NonNull
    private static PropertyElementQuery getExternalPropertyElementQuery(ClassElement defined,
                                                                        ClassElement current,
                                                                        boolean ignoreSettersWithDifferingType) {
        AnnotationMetadataHierarchy hierarchy = new AnnotationMetadataHierarchy(defined, current);
        return PropertyElementQuery.of(hierarchy).ignoreSettersWithDifferingType(ignoreSettersWithDifferingType);
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
                writers.forEach((className, writer) -> {
                    try {
                        writer.accept(visitorContext);
                    } catch (ElementPostponedToNextRoundException ignore) {
                        // Ignore, next round will redo
                    } catch (IOException e) {
                        throw new ClassGenerationException("I/O error occurred during class: '" + className + "' generation: " + e.getMessage(), e);
                    } catch (Throwable e) {
                        throw new RuntimeException("Failed to generate class: '" + className + "': " + e.getMessage(), e);
                    }
                });

            }
        } finally {
            writers.clear();
        }
    }

    private void processElement(boolean metadata,
                                Set<AnnotationValue<Annotation>> indexedAnnotations,
                                ClassElement ce,
                                BeanIntrospectionWriter writer,
                                boolean ignoreSettersWithDifferingType) {

        processElement(metadata,
            indexedAnnotations,
            PropertyElementQuery.of(ce).ignoreSettersWithDifferingType(ignoreSettersWithDifferingType),
            ce,
            writer
        );
    }

    private void handleBuilder(
        ClassElement classToBuild,
        VisitorContext context,
        String creatorMethod,
        String[] writePrefixes,
        MethodElement primaryConstructor,
        MethodElement defaultConstructor,
        ClassElement builderType,
        AnnotationMetadata builderMetadata,
        int index, String targetPackage) {
        if (builderMetadata == null) {
            builderMetadata = AnnotationMetadata.EMPTY_METADATA;
        }
        if (!isIntrospected(context, builderType)) {
            ElementQuery<MethodElement> buildMethodQuery = ElementQuery
                .ALL_METHODS
                .onlyAccessible(classToBuild)
                .onlyInstance()
                .filter(m -> m.getGenericReturnType().getName().equals(classToBuild.getName()));
            if (creatorMethod != null) {
                buildMethodQuery = buildMethodQuery.named(creatorMethod);
            }

            MethodElement creatorMethodElement = builderType.getEnclosedElement(buildMethodQuery).orElse(null);
            if (creatorMethodElement != null) {
                final BeanIntrospectionWriter builderWriter = new BeanIntrospectionWriter(
                    targetPackage,
                    builderType.getName(),
                    index,
                    classToBuild,
                    builderType,
                    builderMetadata,
                    context
                );
                ClassElement callingType = ClassElement.of(builderWriter.getIntrospectionName());
                if (defaultConstructor != null) {
                    if (defaultConstructor.isAccessible(callingType)) {
                        builderWriter.visitDefaultConstructor(defaultConstructor);
                    } else {
                        findBuilderMethodOrFail(classToBuild, context, builderType, callingType, builderWriter);
                    }
                } else if (primaryConstructor != null) {
                    if (primaryConstructor.isAccessible(callingType)) {
                        builderWriter.visitDefaultConstructor(primaryConstructor);
                    } else {
                        findBuilderMethodOrFail(classToBuild, context, builderType, callingType, builderWriter);
                    }
                } else {
                    findBuilderMethodOrFail(classToBuild, context, builderType, callingType, builderWriter);
                }

                builderWriter.visitBeanMethod(creatorMethodElement);

                // search method builder methods and make executable
                ElementQuery<MethodElement> builderMethodQuery = ElementQuery.ALL_METHODS
                    .onlyAccessible(classToBuild)
                    .onlyInstance()
                    .filter(m ->
                        Arrays.stream(writePrefixes).anyMatch(m.getName()::startsWith) &&
                            builderType.isAssignable(m.getGenericReturnType()) && m.getParameters().length <= 1
                    );
                builderType.getEnclosedElements(builderMethodQuery)
                    .forEach(builderWriter::visitBeanMethod);
                writers.put(builderWriter.getBeanType().getName(), builderWriter);
            } else {
                context.fail("No build method found in builder: " + builderType.getName(), classToBuild);
            }
        }
    }

    private static void findBuilderMethodOrFail(ClassElement classToBuild, VisitorContext context, ClassElement builderType, ClassElement callingType, BeanIntrospectionWriter builderWriter) {
        // try to find builder method
        MethodElement methodElement = classToBuild.getEnclosedElement(
            ElementQuery.ALL_METHODS
                .onlyStatic()
                .onlyAccessible(callingType)
                .filter(m -> m.getGenericReturnType().isAssignable(builderType) && !m.hasParameters())
        ).orElse(null);
        if (methodElement == null) {
            context.fail("No accessible constructor or builder() method found for builder: " + builderType.getName(), classToBuild);
        } else {
            builderWriter.visitConstructor(methodElement);
        }
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
                annotationMetadata = mergeAnnotations(beanProperty);
            } else {
                annotationMetadata = AnnotationMetadata.EMPTY_METADATA;
            }

            writer.visitProperty(
                beanProperty.getType().withAnnotationMetadata(annotationMetadata),
                beanProperty.getGenericType().withAnnotationMetadata(annotationMetadata),
                beanProperty.getName(),
                beanProperty.getReadMember().orElse(null),
                beanProperty.getWriteMember().orElse(null),
                beanProperty.getReadType().map(t -> t.withAnnotationMetadata(annotationMetadata)).orElse(null),
                beanProperty.getWriteType().map(t -> t.withAnnotationMetadata(annotationMetadata)).orElse(null),
                beanProperty.isReadOnly()
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

        writers.put(writer.getBeanType().getName(), writer);

        addExecutableMethods(ce, writer, beanProperties);
    }

    private AnnotationMetadata mergeAnnotations(AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
            return hierarchy.merge();
        }
        return annotationMetadata;
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
