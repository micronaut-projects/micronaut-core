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
package io.micronaut.graal.reflect;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Import;
import io.micronaut.context.visitor.BeanImportVisitor;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.ReflectionConfig;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassGenerationException;
import jakarta.inject.Inject;

import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Generates the GraalVM reflect.json file at compilation time.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.1
 */
public class GraalTypeElementVisitor implements TypeElementVisitor<Object, Object> {
    /**
     * The position of the visitor.
     */
    public static final int POSITION = -200;

    private static final TypeHint.AccessType[] DEFAULT_ACCESS_TYPE = {TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS};

    private final boolean isSubclass = getClass() != GraalTypeElementVisitor.class;

    /**
     * Elements that the config originates from.
     */
    private final Set<ClassElement> originatingElements = new HashSet<>();

    @Override
    public int getOrder() {
        return POSITION; // allow mutation of metadata
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return CollectionUtils.setOf(
                ReflectiveAccess.class.getName(),
                TypeHint.class.getName(),
                Import.class.getName(),
                "javax.persistence.Entity",
                "jakarta.persistence.Entity",
                AnnotationUtil.INJECT,
                Inject.class.getName(),
                ReflectionConfig.class.getName(),
                ReflectionConfig.ReflectionConfigList.class.getName()
        );
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        originatingElements.clear();
    }

    @SuppressWarnings("java:S3776")
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!isSubclass && !element.hasStereotype(Deprecated.class)) {
            if (originatingElements.contains(element)) {
                return;
            }
            Map<String, ReflectionConfigData> reflectiveClasses = new LinkedHashMap<>();
            final List<AnnotationValue<ReflectionConfig>> values = element.getAnnotationValuesByType(
                    ReflectionConfig.class);
            for (AnnotationValue<ReflectionConfig> value : values) {
                value.stringValue("type").ifPresent(n -> {
                    final ReflectionConfigData data = resolveClassData(n, reflectiveClasses);
                    data.accessTypes.addAll(
                        Arrays.asList(value.enumValues("accessType", TypeHint.AccessType.class))
                    );
                    data.methods.addAll(
                            value.getAnnotations("methods")
                    );
                    data.fields.addAll(
                            value.getAnnotations("fields")
                    );
                });
            }
            if (element.hasAnnotation(ReflectiveAccess.class)) {
                final String beanName = element.getName();
                addBean(beanName, reflectiveClasses);
                element.getDefaultConstructor().ifPresent(constructor -> processMethodElement(constructor, reflectiveClasses));
                resolveClassData(beanName + "[]", reflectiveClasses);
            }

            element.getEnclosedElements(ElementQuery.ALL_METHODS.annotated(ann -> ann.hasAnnotation(ReflectiveAccess.class)))
                    .forEach(m -> processMethodElement(m, reflectiveClasses));
            element.getEnclosedElements(ElementQuery.ALL_FIELDS.annotated(ann -> ann.hasAnnotation(ReflectiveAccess.class)))
                    .forEach(m -> processFieldElement(m, reflectiveClasses));
            if (!element.isInner()) {
                // Inner classes aren't processed if there is no annotation
                // We might trigger the visitor twice but the originatingElements check should avoid it
                element.getEnclosedElements(ElementQuery.ALL_INNER_CLASSES).forEach(c -> visitClass(c, context));
            }

            if (element.hasAnnotation(TypeHint.class)) {
                final String[] introspectedClasses = element.stringValues(TypeHint.class);
                final TypeHint typeHint = element.synthesize(TypeHint.class);
                TypeHint.AccessType[] accessTypes = DEFAULT_ACCESS_TYPE;

                if (typeHint != null) {
                    accessTypes = typeHint.accessType();
                }
                processClasses(accessTypes, reflectiveClasses, introspectedClasses);
                processClasses(accessTypes, reflectiveClasses, element.getValue(
                                       TypeHint.class,
                                       "typeNames",
                                       String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY
                               )
                );
            }

            if (element.hasAnnotation(Import.class)) {
                final List<ClassElement> beanElements = BeanImportVisitor.collectInjectableElements(element, context);
                for (ClassElement beanElement : beanElements) {
                    processBeanElement(reflectiveClasses, beanElement, true);
                }
            } else if (element.hasStereotype(Bean.class) || element.hasStereotype(AnnotationUtil.SCOPE) || element.hasStereotype(
                    AnnotationUtil.QUALIFIER)) {
                processBeanElement(
                        reflectiveClasses,
                        element,
                        false
                );
                MethodElement me = element.getPrimaryConstructor().orElse(null);
                if (me != null && me.isPrivate() && !me.hasAnnotation(ReflectiveAccess.class)) {
                    processMethodElement(me, reflectiveClasses);
                }
            }

            if (element.isInner()) {
                ClassElement enclosingType = element.getEnclosingType().orElse(null);
                if (enclosingType != null && enclosingType.hasAnnotation(ReflectiveAccess.class)) {
                    final String beanName = element.getName();
                    addBean(beanName, reflectiveClasses);
                    resolveClassData(beanName + "[]", reflectiveClasses);
                }
            }

            if (!reflectiveClasses.isEmpty()) {
                originatingElements.add(element);
                @SuppressWarnings("unchecked") final AnnotationValue<ReflectionConfig>[] annotationValues =
                        reflectiveClasses.values().stream()
                        .map(ReflectionConfigData::build)
                        .toArray(AnnotationValue[]::new);
                MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();

                final AnnotationValue<ReflectionConfig.ReflectionConfigList> av =
                        AnnotationValue.builder(ReflectionConfig.ReflectionConfigList.class)
                        .values(annotationValues)
                        .build();
                annotationMetadata.addAnnotation(
                        av.getAnnotationName(),
                        av.getValues(),
                        RetentionPolicy.RUNTIME
                );
                GraalReflectionMetadataWriter writer = new GraalReflectionMetadataWriter(
                        element,
                        annotationMetadata
                );
                try {
                    writer.accept(context);
                } catch (IOException e) {
                    throw new ClassGenerationException("I/O error occurred during class generation: " + e.getMessage(), e);
                }
            }
        }
    }

    private void processBeanElement(
            Map<String, ReflectionConfigData> reflectiveClasses,
            ClassElement beanElement,
            boolean isImport) {
        processBeanConstructor(reflectiveClasses, beanElement, isImport);

        processBeanMethods(reflectiveClasses, beanElement, isImport);

        processBeanFields(reflectiveClasses, beanElement, isImport);

    }

    private void processBeanFields(Map<String, ReflectionConfigData> reflectiveClasses, ClassElement beanElement, boolean isImport) {
        final ElementQuery<FieldElement> reflectiveFieldQuery = ElementQuery.ALL_FIELDS
                .onlyInstance()
                .onlyInjected();

        if (isImport) {
            // fields that are injected but not public and are imported need reflection
            beanElement
                    .getEnclosedElements(reflectiveFieldQuery.modifiers((elementModifiers -> !elementModifiers.contains(ElementModifier.PUBLIC))))
                    .forEach(e -> processFieldElement(e, reflectiveClasses));
        } else {
            // fields that are injected and private need reflection
            beanElement
                    .getEnclosedElements(reflectiveFieldQuery.modifiers((elementModifiers -> elementModifiers.contains(ElementModifier.PRIVATE))))
                    .forEach(e -> processFieldElement(e, reflectiveClasses));

        }
    }

    private void processBeanMethods(Map<String, ReflectionConfigData> reflectiveClasses, ClassElement beanElement, boolean isImport) {
        ElementQuery<MethodElement> injectedMethodsThatNeedReflection = ElementQuery.ALL_METHODS
                .onlyInstance()
                .onlyInjected();

        if (isImport) {
            final Predicate<Set<ElementModifier>> nonPublicOnly = elementModifiers ->
                    !elementModifiers.contains(ElementModifier.PUBLIC);
            // methods that are injected but not public and are imported need reflection
            beanElement
                    .getEnclosedElements(injectedMethodsThatNeedReflection
                             .modifiers(nonPublicOnly))
                    .forEach(m -> processMethodElement(m, reflectiveClasses));
            beanElement.getEnclosedElements(
                    ElementQuery
                            .ALL_METHODS
                            .onlyInstance()
                            .modifiers(nonPublicOnly)
                            .annotated(ann -> ann.hasAnnotation(Executable.class))
            ).forEach(m -> processMethodElement(m, reflectiveClasses));
        } else {
            final Predicate<Set<ElementModifier>> privateOnly = elementModifiers ->
                    elementModifiers.contains(ElementModifier.PRIVATE);
            beanElement
                    .getEnclosedElements(injectedMethodsThatNeedReflection
                                                 .modifiers(privateOnly))
                    .forEach(m -> processMethodElement(m, reflectiveClasses));
            beanElement.getEnclosedElements(
                    ElementQuery
                            .ALL_METHODS
                            .onlyInstance()
                            .modifiers(privateOnly)
                            .annotated(ann -> ann.hasAnnotation(Executable.class))
            ).forEach(m -> processMethodElement(m, reflectiveClasses));
        }
        // methods with explicit reflective access
        beanElement.getEnclosedElements(
                ElementQuery.ALL_METHODS.annotated(ann -> ann.hasAnnotation(ReflectiveAccess.class))
        ).forEach(m -> processMethodElement(m, reflectiveClasses));
    }

    private void processBeanConstructor(Map<String, ReflectionConfigData> reflectiveClasses, ClassElement beanElement, boolean isImport) {
        final MethodElement constructor = beanElement.getPrimaryConstructor().orElse(null);
        if (constructor != null &&
                (constructor.hasAnnotation(ReflectiveAccess.class) ||
                         (isImport && !constructor.isPublic()) ||
                         (!isImport && constructor.isPrivate()))) {
            processMethodElement(constructor, reflectiveClasses);
        }
    }

    private void addBean(String beanName, Map<String, ReflectionConfigData> reflectiveClasses) {
        resolveClassData(beanName, reflectiveClasses)
                .accessTypes.addAll(
                    Arrays.asList(
                        TypeHint.AccessType.ALL_PUBLIC_METHODS,
                        TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS,
                        TypeHint.AccessType.ALL_DECLARED_FIELDS
                    )
                );
    }

    private void processFieldElement(FieldElement element,
                                     Map<String, ReflectionConfigData> classes) {
        final ClassElement dt = element.getDeclaringType();
        final ReflectionConfigData data = resolveClassData(resolveName(dt).getName(), classes);
        data.fields.add(AnnotationValue.builder(ReflectionConfig.ReflectiveFieldConfig.class)
                                .member("name", element.getName())
                                .build()
        );
    }

    private AnnotationClassValue<?> resolveName(ClassElement classElement) {
        return new AnnotationClassValue<>(classElement.getCanonicalName());
    }

    private void processMethodElement(MethodElement element, Map<String, ReflectionConfigData> classes) {
        final String methodName = element.getName();
        final ClassElement declaringType = element.getDeclaringType();
        final ReflectionConfigData data = resolveClassData(declaringType.getName(), classes);
        final List<AnnotationClassValue<?>> params = Arrays.stream(element.getParameters())
                .map(ParameterElement::getType)
                .map(this::resolveName).collect(Collectors.toList());
        data.methods.add(
                AnnotationValue.builder(ReflectionConfig.ReflectiveMethodConfig.class)
                        .member("name", methodName)
                        .member("parameterTypes", params.toArray(AnnotationClassValue.EMPTY_ARRAY))
                        .build()
        );
    }

    private void processClasses(TypeHint.AccessType[] accessType, Map<String, ReflectionConfigData> reflectiveClasses, String... introspectedClasses) {
        for (TypeHint.AccessType type : accessType) {
            if (type == TypeHint.AccessType.ALL_PUBLIC) {
                for (String aClass : introspectedClasses) {
                    addBean(aClass, reflectiveClasses);
                }
                return;
            }
        }
        for (String introspectedClass : introspectedClasses) {
            resolveClassData(introspectedClass, reflectiveClasses)
                    .accessTypes.addAll(Arrays.asList(accessType));
        }
    }

    private ReflectionConfigData resolveClassData(String introspectedClass, Map<String, ReflectionConfigData> classes) {
        return classes.computeIfAbsent(introspectedClass, s -> new ReflectionConfigData(introspectedClass));
    }


    private static final class ReflectionConfigData {
        private final AnnotationClassValue<?> type;
        private final List<TypeHint.AccessType> accessTypes = new ArrayList<>(5);
        private final List<AnnotationValue<ReflectionConfig.ReflectiveMethodConfig>> methods = new ArrayList<>(30);
        private final List<AnnotationValue<ReflectionConfig.ReflectiveFieldConfig>> fields = new ArrayList<>(30);

        ReflectionConfigData(String type) {
            this.type = new AnnotationClassValue<>(type);
        }

        AnnotationValue<ReflectionConfig> build() {
            final AnnotationValueBuilder<ReflectionConfig> builder = AnnotationValue.builder(ReflectionConfig.class)
                    .member("type", type)
                    .member("accessType", accessTypes.toArray(new TypeHint.AccessType[0]));
            if (!methods.isEmpty()) {
                builder.member("methods", methods.toArray(new AnnotationValue<?>[0]));
            }
            if (!fields.isEmpty()) {
                builder.member("fields", fields.toArray(new AnnotationValue<?>[0]));
            }
            return builder
                    .build();
        }
    }
}
