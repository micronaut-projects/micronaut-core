/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassGenerationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
public class IntrospectedTypeElementVisitor implements TypeElementVisitor<Object, Object> {

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
    private List<AbstractIntrospection> abstractIntrospections = new ArrayList<>();
    private ClassElement lastConfigurationReader;
    private AbstractIntrospection currentAbstractIntrospection;

    @Override
    public int getOrder() {
        // lower precedence, all others to mutate metadata as necessary
        return POSITION;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        // reset
        currentAbstractIntrospection = null;
        lastConfigurationReader = null;
        if (!element.isPrivate()) {
            if (element.hasStereotype(Introspected.class)) {
                final AnnotationValue<Introspected> introspected = element.getAnnotation(Introspected.class);
                if (introspected != null && !writers.containsKey(element.getName())) {
                    processIntrospected(element, context, introspected);
                }
            } else if (element.hasStereotype(ConfigurationReader.class)) {
                this.lastConfigurationReader = element;
            }
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        final ClassElement declaringType = element.getDeclaringType();
        if (lastConfigurationReader != null && lastConfigurationReader.getName().equals(declaringType.getName())) {
            final ParameterElement[] parameters = element.getParameters();
            introspectIfValidated(context, declaringType, parameters);
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        final ClassElement declaringType = element.getDeclaringType();
        final String methodName = element.getName();
        if (lastConfigurationReader != null && lastConfigurationReader.getName().equals(declaringType.getName())) {
            if (methodName.startsWith("get")) {
                final boolean hasConstraints = element.hasStereotype(JAVAX_VALIDATION_CONSTRAINT) || element.hasStereotype(JAVAX_VALIDATION_VALID);
                if (hasConstraints) {
                    processIntrospected(declaringType, context, AnnotationValue.builder(Introspected.class).build());
                }
            }
        }

        if (currentAbstractIntrospection != null) {
            if (NameUtils.isGetterName(methodName) && element.getParameters().length == 0) {
                final String propertyName = NameUtils.getPropertyNameForGetter(methodName);
                final AbstractPropertyElement propertyElement = currentAbstractIntrospection.properties.computeIfAbsent(propertyName, s -> new AbstractPropertyElement(
                        element.getDeclaringType(),
                        element.getReturnType(),
                        propertyName
                ));
                propertyElement.readMethod = element;
            } else if (NameUtils.isSetterName(methodName) && element.getParameters().length == 1) {
                final String propertyName = NameUtils.getPropertyNameForSetter(methodName);
                final AbstractPropertyElement propertyElement = currentAbstractIntrospection.properties.computeIfAbsent(propertyName, s -> new AbstractPropertyElement(
                        element.getDeclaringType(),
                        element.getParameters()[0].getType(),
                        propertyName
                ));
                propertyElement.writeMethod = element;
            }
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        final ClassElement declaringType = element.getDeclaringType();
        if (lastConfigurationReader != null && lastConfigurationReader.getName().equals(declaringType.getName())) {
            if (!writers.containsKey(declaringType.getName())) {
                final boolean hasConstraints = element.hasStereotype(JAVAX_VALIDATION_CONSTRAINT) || element.hasStereotype(JAVAX_VALIDATION_VALID);
                if (hasConstraints) {
                    processIntrospected(declaringType, context, AnnotationValue.builder(Introspected.class).build());
                }
            }
        }
    }

    private void introspectIfValidated(VisitorContext context, ClassElement declaringType, ParameterElement[] parameters) {
        if (!writers.containsKey(declaringType.getName())) {
            final boolean hasConstraints = Arrays.stream(parameters).anyMatch(e ->
                    e.hasStereotype(JAVAX_VALIDATION_CONSTRAINT) || e.hasStereotype(JAVAX_VALIDATION_VALID)
            );
            if (hasConstraints) {
                processIntrospected(declaringType, context, AnnotationValue.builder(Introspected.class).build());
            }
        }
    }

    private void processIntrospected(ClassElement element, VisitorContext context, AnnotationValue<Introspected> introspected) {
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
                    if (!ce.isAbstract() && ce.isPublic() && !ce.hasStereotype(Introspected.class)) {
                        final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                                element.getName(),
                                index.getAndIncrement(),
                                ce,
                                metadata ? element.getAnnotationMetadata() : null
                        );

                        processElement(metadata, includes, excludes, excludedAnnotations, indexedAnnotations, ce, writer);
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
                        if (classElement.isAbstract() || !classElement.isPublic() || classElement.hasStereotype(Introspected.class)) {
                            continue;
                        }
                        final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                                element.getName(),
                                j++,
                                classElement,
                                metadata ? element.getAnnotationMetadata() : null
                        );

                        processElement(metadata, includes, excludes, excludedAnnotations, indexedAnnotations, classElement, writer);
                    }
                }
            }
        } else {

            final BeanIntrospectionWriter writer = new BeanIntrospectionWriter(
                    element,
                    metadata ? element.getAnnotationMetadata() : null
            );

            processElement(metadata, includes, excludes, excludedAnnotations, indexedAnnotations, element, writer);
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {

        for (AbstractIntrospection abstractIntrospection : abstractIntrospections) {
            final Collection<? extends PropertyElement> properties = abstractIntrospection.properties.values();
            if (CollectionUtils.isNotEmpty(properties)) {
                processBeanProperties(
                        abstractIntrospection.writer,
                        properties,
                        abstractIntrospection.includes,
                        abstractIntrospection.excludes,
                        abstractIntrospection.ignored,
                        abstractIntrospection.indexedAnnotations,
                        abstractIntrospection.metadata
                );
                writers.put(abstractIntrospection.writer.getBeanType().getClassName(), abstractIntrospection.writer);
            }

        }

        for (BeanIntrospectionWriter writer : writers.values()) {
            try {
                writer.accept(visitorContext);
            } catch (IOException e) {
                throw new ClassGenerationException("I/O error occurred during class generation: " + e.getMessage(), e);
            }
        }

    }

    private void processElement(
            boolean metadata,
            Set<String> includes,
            Set<String> excludes,
            Set<String> excludedAnnotations,
            Set<AnnotationValue> indexedAnnotations,
            ClassElement ce,
            BeanIntrospectionWriter writer) {
        if (ce.isAbstract()) {
            currentAbstractIntrospection = new AbstractIntrospection(
                    writer,
                    includes,
                    excludes,
                    excludedAnnotations,
                    indexedAnnotations,
                    metadata
            );
            abstractIntrospections.add(currentAbstractIntrospection);
        } else {
            final List<PropertyElement> beanProperties = ce.getBeanProperties();
            Optional<MethodElement> constructorElement = ce.getPrimaryConstructor();

            final MethodElement constructor = constructorElement.orElse(null);
            process(
                    constructor,
                    ce.getDefaultConstructor().orElse(null),
                    writer,
                    beanProperties,
                    includes,
                    excludes,
                    excludedAnnotations,
                    indexedAnnotations,
                    metadata
            );
        }
    }

    private void process(
            @Nullable MethodElement constructorElement,
            @Nullable MethodElement defaultConstructor,
            BeanIntrospectionWriter writer,
            List<PropertyElement> beanProperties,
            Set<String> includes,
            Set<String> excludes,
            Set<String> ignored,
            Set<AnnotationValue> indexedAnnotations,
            boolean metadata) {

        if (constructorElement != null) {
            final ParameterElement[] parameters = constructorElement.getParameters();
            if (ArrayUtils.isNotEmpty(parameters)) {
                writer.visitConstructor(constructorElement);
            }
        }
        if (defaultConstructor != null) {
            writer.visitDefaultConstructor(defaultConstructor);
        }

        processBeanProperties(writer, beanProperties, includes, excludes, ignored, indexedAnnotations, metadata);
        writers.put(writer.getBeanType().getClassName(), writer);
    }

    private void processBeanProperties(BeanIntrospectionWriter writer, Collection<? extends PropertyElement> beanProperties, Set<String> includes, Set<String> excludes, Set<String> ignored, Set<AnnotationValue> indexedAnnotations, boolean metadata) {
        for (PropertyElement beanProperty : beanProperties) {
            final ClassElement type = beanProperty.getType();

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

    /**
     * Holder for an abstract introspection.
     */
    private class AbstractIntrospection {
        final BeanIntrospectionWriter writer;
        final Set<String> includes;
        final Set<String> excludes;
        final Set<String> ignored;
        final Set<AnnotationValue> indexedAnnotations;
        final boolean metadata;
        final Map<String, AbstractPropertyElement> properties = new LinkedHashMap<>();

        public AbstractIntrospection(
                BeanIntrospectionWriter writer,
                Set<String> includes,
                Set<String> excludes,
                Set<String> ignored,
                Set<AnnotationValue> indexedAnnotations,
                boolean metadata) {
            this.writer = writer;
            this.includes = includes;
            this.excludes = excludes;
            this.ignored = ignored;
            this.indexedAnnotations = indexedAnnotations;
            this.metadata = metadata;
        }
    }

    /**
     * Used to accumulate property elements for abstract types.
     */
    private class AbstractPropertyElement implements PropertyElement {

        private final ClassElement declaringType;
        private final ClassElement type;
        private final String name;

        private MethodElement writeMethod;
        private MethodElement readMethod;

        AbstractPropertyElement(ClassElement declaringType, ClassElement type, String name) {
            this.declaringType = declaringType;
            this.type = type;
            this.name = name;
        }

        @Override
        public Optional<MethodElement> getWriteMethod() {
            return Optional.ofNullable(writeMethod);
        }

        @Override
        public Optional<MethodElement> getReadMethod() {
            return Optional.ofNullable(readMethod);
        }

        @Nonnull
        @Override
        public ClassElement getType() {
            return type;
        }

        @Override
        public ClassElement getDeclaringType() {
            return declaringType;
        }

        @Nonnull
        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isProtected() {
            return false;
        }

        @Override
        public boolean isPublic() {
            return true;
        }

        @SuppressWarnings("ConstantConditions")
        @Nonnull
        @Override
        public Object getNativeType() {
            throw null;
        }
    }

}
