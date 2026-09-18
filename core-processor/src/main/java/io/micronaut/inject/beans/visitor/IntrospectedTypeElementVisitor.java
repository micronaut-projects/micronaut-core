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
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.processing.definition.OutputObjectDef;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.ImportedClass;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.inject.writer.OriginatingElements;
import io.micronaut.sourcegen.model.ObjectDef;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final Set<String> processed = new HashSet<>();
    /**
     * The introspections written during this compilation, keyed by the generated introspection class
     * name and holding the name of the type the introspection was generated for. An introspection
     * generated on behalf of another element (via {@link Introspected#classNames()},
     * {@link Introspected#classes()} or {@link io.micronaut.context.annotation.ClassImport}) is not
     * named after the type it introspects, so this is the only reliable way to detect that the same
     * introspection is about to be written twice.
     */
    private final Map<String, String> writtenIntrospections = new HashMap<>();

    @Override
    public int getOrder() {
        // lower precedence, all others to mutate metadata as necessary
        return POSITION;
    }

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasStereotype(Introspected.class)) {
            final AnnotationValue<Introspected> introspected = element.getAnnotation(Introspected.class);
            if (introspected != null && !processed.contains(element.getName())) {
                processIntrospected(element, context, introspected);
            }
        }
    }

    private boolean isIntrospected(VisitorContext context, ClassElement c) {
        return processed.contains(c.getName()) || context.getClassElement(c.getPackageName() + ".$" + c.getSimpleName() + "$Introspection").isPresent();
    }

    /**
     * Claims the introspection about to be written by the given writer.
     *
     * @param beanClassElement The introspected type
     * @param writer           The writer
     * @return {@code true} if the very same introspection was already written during this compilation
     * and should not be written again
     */
    private boolean isAlreadyWritten(ClassElement beanClassElement, BeanIntrospectionWriter writer) {
        String introspectionName = writer.getIntrospectionName();
        String previous = writtenIntrospections.putIfAbsent(introspectionName, beanClassElement.getName());
        if (previous == null) {
            return false;
        }
        if (!previous.equals(beanClassElement.getName())) {
            throw new ProcessingException(beanClassElement, "Introspection '" + introspectionName
                + "' cannot be generated for '" + beanClassElement.getName()
                + "' because it is already generated for '" + previous + "'");
        }
        return true;
    }

    private void processIntrospected(ClassElement element, VisitorContext context, AnnotationValue<Introspected> introspected) {
        boolean ignoreSettersWithDifferingType = introspected.booleanValue("ignoreSettersWithDifferingType").orElse(true);
        final String[] packages = introspected.stringValues("packages");
        final List<String> classes = Stream.concat(
            Arrays.stream(introspected.annotationClassValues("classes")).map(AnnotationClassValue::getName),
            Arrays.stream(introspected.stringValues("classNames"))
        ).toList();
        final boolean metadata = introspected.booleanValue("annotationMetadata").orElse(true);
        final boolean members = metadata && introspected.booleanValue("members").orElse(false);
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
                processBuilderDefinition(ce, context, ce.findAnnotation(Introspected.class).orElse(introspected), introspectionIndex, targetPackage, true);
                final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                    targetPackage,
                    element.getName(),
                    introspectionIndex,
                    element,
                    ce,
                    metadata ? ce.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA,
                    context
                );

                processElement(
                    metadata,
                    members,
                    indexedAnnotations,
                    getExternalPropertyElementQuery(element, ce, ignoreSettersWithDifferingType),
                    ce,
                    writer,
                    isDescribeConstructors(ce, introspected),
                    context
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
                        processBuilderDefinition(classElement, context, classElement.findAnnotation(Introspected.class).orElse(introspected), introspectionIndex, targetPackage, true);
                        final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                            targetPackage,
                            element.getName(),
                            introspectionIndex,
                            element,
                            classElement,
                            metadata ? classElement.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA,
                            context
                        );


                        processElement(metadata,
                            members,
                            indexedAnnotations,
                            getExternalPropertyElementQuery(element, classElement, ignoreSettersWithDifferingType),
                            classElement,
                            writer,
                            isDescribeConstructors(classElement, introspected),
                            context);
                    }
                }
            }
        } else {
            processBuilderDefinition(element, context, introspected, 0, targetPackage, element.hasAnnotation(ImportedClass.class));
            final BeanIntrospectionWriter writer;
            if (element.hasAnnotation(ImportedClass.class)) {
                ClassElement originatingElement = context.getClassElement(element.stringValue(ImportedClass.class, "originatingElement").orElseThrow()).orElseThrow();
                writer = new BeanIntrospectionWriter(
                element.stringValue(ImportedClass.class, "targetPackage")
                    .orElse(element.getPackageName()),
                    element.getName(),
                    0,
                    originatingElement,
                    element,
                    metadata ? element.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA,
                    context
                );
            } else {
                writer = new BeanIntrospectionWriter(
                    targetPackage,
                    element,
                    metadata ? element.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA,
                    context
                );
            }
            processElement(metadata, members, indexedAnnotations, element, writer, ignoreSettersWithDifferingType, isDescribeConstructors(element, introspected), context);
        }
    }

    private static boolean isDescribeConstructors(ClassElement ce, AnnotationValue<Introspected> introspected) {
        return ce.findAnnotation(Introspected.class).orElse(introspected).booleanValue("constructors").orElse(false);
    }

    private void processBuilderDefinition(ClassElement element, VisitorContext context, AnnotationValue<Introspected> introspected, int index, String targetPackage, boolean useLongBuilderName) {
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
                builderClass,
                useLongBuilderName
            );
        } else if (element.hasDeclaredAnnotation(ANN_LOMBOK_BUILDER)) {
            AnnotationValue<Annotation> lombokBuilder = Objects.requireNonNull(element.getDeclaredAnnotation(ANN_LOMBOK_BUILDER));
            String lombokBuilderAccessType = lombokBuilder.stringValue("access").orElse("");
            if ("PRIVATE".equals(lombokBuilderAccessType)) {
                return;
            }
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
                null,
                useLongBuilderName
            );
        }
    }

    private void processBuilderDefinition(ClassElement element, VisitorContext context, AnnotationValue<Introspected> introspected, int index, String targetPackage, @Nullable String builderMethod, @Nullable String creatorMethod, String[] writePrefixes, @Nullable AnnotationClassValue<?> builderClass, boolean useLongBuilderName) {
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
                        targetPackage,
                        useLongBuilderName
                    );
                } else {
                    throw new ProcessingException(methodElement, "Builder return type is not public. The method must be static and accessible.");
                }
            } else {
                throw new ProcessingException(element, "Method " + builderMethod + "() specified by builderMethod not found. The method must be static and accessible.");
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
                    targetPackage,
                    useLongBuilderName);
            } else {
                throw new ProcessingException(element, "Builder class not found on compilation classpath: " + builderClass.getName());
            }
        } else {
            throw new ProcessingException(element, "When specifying the 'builder' member of @Introspected you must supply either a builderClass or builderMethod");
        }
    }

    private static PropertyElementQuery getExternalPropertyElementQuery(ClassElement defined,
                                                                        ClassElement current,
                                                                        boolean ignoreSettersWithDifferingType) {
        AnnotationMetadataHierarchy hierarchy = new AnnotationMetadataHierarchy(defined, current);
        return PropertyElementQuery.of(hierarchy).ignoreSettersWithDifferingType(ignoreSettersWithDifferingType);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    private void write(OutputObjectDef outputObjectDef, VisitorContext visitorContext) {
        try {
            ObjectDef objectDef = outputObjectDef.objectDef();
            Class<?> serviceClass = outputObjectDef.serviceClass();
            OriginatingElements originatingElements = outputObjectDef.originatingElements();
            if (serviceClass != null) {
                visitorContext.visitServiceDescriptor(serviceClass, objectDef.getName(), originatingElements.getOriginatingElements()[0]);
            }
            try (OutputStream outputStream = visitorContext.visitClass(objectDef.getName(), originatingElements.getOriginatingElements())) {
                outputStream.write(ByteCodeWriterUtils.writeByteCode(objectDef, visitorContext));
            }
        } catch (ElementPostponedToNextRoundException ignore) {
            // Ignore, next round will redo
        } catch (IOException e) {
            // raise a compile error
            String message = e.getMessage();
            throw new ProcessingException(outputObjectDef.originatingElements().getOriginatingElements()[0], "Unexpected error: " + (message != null ? message : e.getClass().getSimpleName()));
        } catch (Throwable e) {
            throw new ProcessingException(outputObjectDef.originatingElements().getOriginatingElements()[0], "Failed to generate class: '" + outputObjectDef.objectDef().getName() + "': " + e.getMessage(), e);
        }
    }

    private void processElement(boolean metadata,
                                boolean members,
                                Set<AnnotationValue<Annotation>> indexedAnnotations,
                                ClassElement ce,
                                BeanIntrospectionWriter writer,
                                boolean ignoreSettersWithDifferingType,
                                boolean describeConstructors,
                                VisitorContext visitorContext) {

        processElement(metadata,
            members,
            indexedAnnotations,
            PropertyElementQuery.of(ce).ignoreSettersWithDifferingType(ignoreSettersWithDifferingType),
            ce,
            writer,
            describeConstructors,
            visitorContext
        );
    }

    private void handleBuilder(
        ClassElement classToBuild,
        VisitorContext context,
        @Nullable String creatorMethod,
        String[] writePrefixes,
        @Nullable MethodElement primaryConstructor,
        @Nullable MethodElement defaultConstructor,
        ClassElement builderType,
        @Nullable AnnotationMetadata builderMetadata,
        int index,
        String targetPackage,
        boolean useLongBuilderName) {
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
                final BeanIntrospectionWriter builderWriter;
                if (useLongBuilderName) {
                    builderWriter = new BeanIntrospectionWriter(
                        targetPackage,
                        builderType.getName(),
                        index,
                        classToBuild,
                        builderType,
                        builderMetadata,
                        context
                    );
                } else {
                    builderWriter = new BeanIntrospectionWriter(
                        targetPackage,
                        builderType,
                        builderMetadata,
                        context
                    );
                }
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

                processed.add(classToBuild.getName());
                if (isAlreadyWritten(builderType, builderWriter)) {
                    return;
                }
                for (OutputObjectDef outputObjectDef : builderWriter.build()) {
                    write(outputObjectDef, context);
                }
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
                                boolean members,
                                Set<AnnotationValue<Annotation>> indexedAnnotations,
                                PropertyElementQuery propertyElementQuery,
                                ClassElement ce,
                                BeanIntrospectionWriter writer,
                                boolean describeConstructors,
                                VisitorContext context) {
        if (isAlreadyWritten(ce, writer)) {
            processed.add(ce.getName());
            return;
        }
        List<PropertyElement> beanProperties = ce.getBeanProperties(propertyElementQuery).stream()
            .filter(p -> !p.isExcluded())
            .toList();
        if (members) {
            writer.describeMembers();
        }
        Optional<MethodElement> constructorElement = ce.getPrimaryConstructor();
        constructorElement.ifPresent(constructorEl -> {
            if (ArrayUtils.isNotEmpty(constructorEl.getParameters())) {
                writer.visitConstructor(constructorEl);
            }
        });
        ce.getDefaultConstructor().ifPresent(writer::visitDefaultConstructor);

        if (!ce.isEnum()) {
            for (MethodElement declaredConstructor : ce.getEnclosedElements(ElementQuery.CONSTRUCTORS)) {
                if (describeConstructors || declaredConstructor.hasDeclaredStereotype(Executable.class)) {
                    writer.visitDeclaredConstructor(declaredConstructor);
                }
            }
        }

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
                beanProperty.isReadOnly(),
                members ? resolvePropertyMembers(ce, beanProperty) : List.of()
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

        addExecutableMethods(ce, writer, beanProperties);

        processed.add(ce.getName());
        for (OutputObjectDef outputObjectDef : writer.build()) {
            write(outputObjectDef, context);
        }
    }

    private AnnotationMetadata mergeAnnotations(AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
            return hierarchy.merge();
        }
        return annotationMetadata;
    }

    /**
     * Resolves the individual members (the field, the read methods and the write methods) a property is composed
     * of, each with its own type and its own annotation metadata: the field, and the read and write method of
     * every type of the hierarchy declaring one, each carrying the annotations of its own declaration and not the
     * ones of the methods it overrides, so that a member is attributed to the type declaring it.
     *
     * @param beanType     The introspected type
     * @param beanProperty The property
     * @return The members, in field, read methods, write methods order, the declaration of the most specific
     * type first in each group
     */
    private List<BeanIntrospectionWriter.PropertyMemberDef> resolvePropertyMembers(ClassElement beanType, PropertyElement beanProperty) {
        List<BeanIntrospectionWriter.PropertyMemberDef> members = new ArrayList<>(3);
        beanProperty.getField().ifPresent(field -> {
            for (FieldElement declaration : fieldDeclarations(beanType, field)) {
                members.add(new BeanIntrospectionWriter.PropertyMemberDef(
                    declaration,
                    // the field of the property is read as the property reads it, a field it hides through
                    // the class declaring it
                    declaration == field ? field : null,
                    declaration.getGenericType().withAnnotationMetadata(memberAnnotationMetadata(declaration, declaration.getType()))
                ));
            }
        });
        beanProperty.getReadMethod()
            .filter(method -> !method.isSynthetic())
            .ifPresent(method -> {
                for (MethodElement declaration : declarations(beanType, method)) {
                    members.add(new BeanIntrospectionWriter.PropertyMemberDef(
                        declaration,
                        method,
                        declaration.getGenericReturnType().withAnnotationMetadata(
                            memberAnnotationMetadata(declaration.getDeclaredMethodAnnotationMetadata(), declaration.getReturnType())
                        )
                    ));
                }
            });
        beanProperty.getWriteMethod()
            .filter(method -> !method.isSynthetic() && method.getParameters().length == 1)
            .ifPresent(method -> {
                for (MethodElement declaration : declarations(beanType, method)) {
                    ParameterElement[] parameters = declaration.getParameters();
                    if (parameters.length != 1) {
                        continue;
                    }
                    ParameterElement parameter = parameters[0];
                    members.add(new BeanIntrospectionWriter.PropertyMemberDef(
                        declaration,
                        method,
                        parameter.getGenericType().withAnnotationMetadata(
                            memberAnnotationMetadata(declaration.getDeclaredMethodAnnotationMetadata(), parameter.getType())
                        )
                    ));
                }
            });
        return members;
    }

    /**
     * The declarations of a field: the field itself and the fields of the same name it hides in the super
     * classes, each a member of the property with the annotations of its own declaration, the bean type first.
     *
     * @param beanType The introspected type
     * @param field    The field of the property
     * @return The declarations, the most specific first
     */
    private static List<FieldElement> fieldDeclarations(ClassElement beanType, FieldElement field) {
        List<FieldElement> hidden = beanType.getEnclosedElements(
            ElementQuery.ALL_FIELDS.onlyInstance().includeHiddenElements().named(field.getName())
        );
        if (hidden.size() < 2) {
            return List.of(field);
        }
        Set<String> declaringTypes = new HashSet<>();
        declaringTypes.add(field.getDeclaringType().getName());
        List<FieldElement> declarations = new ArrayList<>(hidden.size());
        declarations.add(field);
        for (FieldElement declaration : hidden) {
            if (!declaration.isSynthetic() && declaringTypes.add(declaration.getDeclaringType().getName())) {
                declarations.add(declaration);
            }
        }
        if (declarations.size() > 1) {
            List<String> hierarchy = hierarchyOf(beanType);
            declarations.sort(Comparator.comparingInt(declaration -> rankOf(hierarchy, declaration.getDeclaringType().getName())));
        }
        return declarations;
    }

    /**
     * The declarations of an accessor: the method itself, every method it overrides, and every method of the
     * same signature the hierarchy declares beside it - an interface inheriting an accessor from two parent
     * interfaces without redeclaring it overrides neither - one per type declaring it, the bean type first,
     * then its super classes, then its interfaces.
     *
     * @param beanType The introspected type
     * @param method   The accessor the bean type declares or inherits
     * @return The declarations, the most specific first
     */
    private static List<MethodElement> declarations(ClassElement beanType, MethodElement method) {
        Set<String> declaringTypes = new HashSet<>();
        declaringTypes.add(method.getDeclaringType().getName());
        List<MethodElement> declarations = new ArrayList<>(3);
        declarations.add(method);
        List<MethodElement> candidates = new ArrayList<>(method.getOverriddenMethods());
        candidates.addAll(beanType.getEnclosedElements(
            ElementQuery.ALL_METHODS.onlyInstance().includeOverriddenMethods().named(method.getName())
                .filter(candidate -> hasSameParameterTypes(candidate, method))
        ));
        for (MethodElement declaration : candidates) {
            // a type declares an accessor once; an accessor found through more than one path of the hierarchy
            // is one declaration
            if (!declaration.isSynthetic() && declaringTypes.add(declaration.getDeclaringType().getName())) {
                declarations.add(declaration);
            }
        }
        if (declarations.size() > 1) {
            List<String> hierarchy = hierarchyOf(beanType);
            declarations.sort(Comparator.comparingInt(declaration -> rankOf(hierarchy, declaration.getDeclaringType().getName())));
        }
        return declarations;
    }

    private static boolean hasSameParameterTypes(MethodElement candidate, MethodElement method) {
        ParameterElement[] candidateParameters = candidate.getParameters();
        ParameterElement[] parameters = method.getParameters();
        if (candidateParameters.length != parameters.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!candidateParameters[i].getType().getName().equals(parameters[i].getType().getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * The names of the types of a hierarchy, the type first, then its super classes, then the interfaces of
     * each of them, an interface before the ones it extends: the order the declarations of a member are
     * reported in.
     */
    private static List<String> hierarchyOf(ClassElement type) {
        List<ClassElement> classes = new ArrayList<>();
        for (ClassElement current = type; current != null && !current.getName().equals(Object.class.getName()); current = current.getSuperType().orElse(null)) {
            classes.add(current);
        }
        Set<String> hierarchy = new LinkedHashSet<>();
        for (ClassElement aClass : classes) {
            hierarchy.add(aClass.getName());
        }
        for (ClassElement aClass : classes) {
            collectInterfaces(aClass, hierarchy);
        }
        return new ArrayList<>(hierarchy);
    }

    private static void collectInterfaces(ClassElement type, Set<String> hierarchy) {
        for (ClassElement anInterface : type.getInterfaces()) {
            if (hierarchy.add(anInterface.getName())) {
                collectInterfaces(anInterface, hierarchy);
            }
        }
    }

    private static int rankOf(List<String> hierarchy, String typeName) {
        int rank = hierarchy.indexOf(typeName);
        return rank == -1 ? Integer.MAX_VALUE : rank;
    }

    /**
     * Combines the annotation metadata declared on the member itself with the type annotations of the member's type,
     * mirroring how the annotation metadata of the merged property is assembled.
     *
     * @param memberAnnotationMetadata The annotation metadata of the member
     * @param type                     The type of the member
     * @return The combined annotation metadata
     */
    private AnnotationMetadata memberAnnotationMetadata(AnnotationMetadata memberAnnotationMetadata, ClassElement type) {
        AnnotationMetadata typeAnnotationMetadata = type.getTypeAnnotationMetadata();
        if (typeAnnotationMetadata.isEmpty()) {
            return mergeAnnotations(memberAnnotationMetadata);
        }
        return new AnnotationMetadataHierarchy(true, memberAnnotationMetadata, typeAnnotationMetadata).merge();
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
