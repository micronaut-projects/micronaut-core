/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.visitor;

import io.micronaut.context.annotation.ClassImport;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.ImportedClass;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Internal
public class VisitorUtils {

    @NonNull
    public static List<ClassElement> collectImportedElements(ClassElement element, VisitorContext context) {
        List<ClassElement> importedElements = new ArrayList<>();
        AnnotationValue<ClassImport> annotation = element.getAnnotation(ClassImport.class);
        if (annotation == null) {
            return importedElements;
        }
        String[] includedAnnotations = annotation.stringValues("includedAnnotations");
        String[] excludedAnnotations = annotation.stringValues("excludedAnnotations");
        final String[] classNames = annotation.stringValues("classes");
        if (ArrayUtils.isNotEmpty(classNames)) {
            for (String className : classNames) {
                ClassElement classElement = context.getClassElement(className).orElse(null);
                if (classElement == null) {
                    context.fail("Cannot access class [" + className + "]", element);
                    continue;
                }
                importedElements.add(classElement);
            }
        } else {
            if (includedAnnotations.length == 0) {
                includedAnnotations = new String[]{"*"};
            }
            for (String aPackage : annotation.stringValues("packages")) {
                final ClassElement[] classElements = context.getClassElements(aPackage, includedAnnotations);
                classes:
                for (ClassElement classElement : classElements) {
                    for (String excludedAnnotation : excludedAnnotations) {
                        if (classElement.hasAnnotation(excludedAnnotation)) {
                            continue classes;
                        }
                    }
                    importedElements.add(classElement);
                }
            }
        }
        String targetPackage = annotation.stringValue("targetPackage").orElse(element.getPackageName());
        String[] annotate = annotation.stringValues("annotate");
        for (ClassElement classElement : importedElements) {
            classElement.annotate(ImportedClass.class,
                builder -> builder.member("targetPackage", targetPackage)
                    .member("originatingElement", element.getName()));
            for (String newAnnotation : annotate) {
                classElement.annotate(newAnnotation);
            }
        }
        return importedElements;
    }

    /**
     * Applies a mixin to the target class element during compile-time processing. This involves transferring annotations, fields, methods,
     * and properties from the mixin class to the target class, while adhering to specified conditions and filters. It modifies the target class
     * by augmenting its metadata based on the provided mixin configuration.
     *
     * @param mixinAnnotation The {@code AnnotationValue} representing the {@code @Mixin} annotation applied to the mixin class, used to retrieve filter settings.
     * @param mixin           The {@code ClassElement} representing the mixin class, which contains the annotations, methods, fields, and properties to be applied.
     * @param mixinTarget     The {@code ClassElement} representing the target class to which the mixin is applied, which will be augmented with elements from the mixin class.
     * @param visitorContext  The visitorContext
     */
    public static void applyMixin(AnnotationValue<Mixin> mixinAnnotation, ClassElement mixin, ClassElement mixinTarget, VisitorContext visitorContext) {
        copyAnnotations(mixin, mixinTarget, createPredicate(mixin), findAnnotationsToRemove(mixin));

        final Map<String, FieldElement> mixinFields = mixin.getEnclosedElements(
            ElementQuery.ALL_FIELDS
                .onlyInstance()
                .onlyDeclared()
        ).stream().collect(Collectors.toMap(FieldElement::getName, (e) -> e));

        final MethodElement mixinCtor = mixin.getPrimaryConstructor().orElse(null);
        MethodElement targetCtor = mixinTarget.getPrimaryConstructor().orElse(null);
        if (mixinCtor != null && targetCtor != null) {
            if (!argumentsMatch(mixinCtor, targetCtor)) {
                // The mixin constructor and the primary constructor doesn't match,
                // lets try to find a matching one and mark it as a primary
                MethodElement prevCtor = targetCtor;
                targetCtor = mixinTarget.getAccessibleConstructors().stream().filter(c -> argumentsMatch(mixinCtor, c)).findFirst().orElse(null);
                if (targetCtor != null) {
                    targetCtor.annotate(Creator.class);
                    prevCtor.removeAnnotation(Creator.class);
                }
            }
            if (targetCtor != null) {
                copyAnnotations(mixinCtor, targetCtor, createPredicate(new AnnotationMetadataHierarchy(mixinCtor, mixin)));
            }
        }

        final List<MethodElement> mixinMethods = mixin.isRecord() ? Collections.emptyList() : new ArrayList<>(mixin.getEnclosedElements(
            ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared()
        ));

        final List<PropertyElement> targetProperties = mixinTarget.getBeanProperties();
        for (PropertyElement targetProperty : targetProperties) {
            final FieldElement mixinField = mixinFields.remove(targetProperty.getName());
            if (mixinField != null && mixinField.getType().equals(targetProperty.getType())) {
                AnnotationMetadataHierarchy metadata = new AnnotationMetadataHierarchy(mixinField, mixin);
                copyAnnotations(mixinField, targetProperty, createPredicate(metadata), findAnnotationsToRemove(metadata));
                continue;
            }

            if (CollectionUtils.isNotEmpty(mixinMethods)) {
                final MethodElement readMethod = targetProperty.getReadMethod().orElse(null);
                final MethodElement writeMethod = targetProperty.getWriteMethod().orElse(null);
                final Iterator<MethodElement> i = mixinMethods.iterator();
                while (i.hasNext()) {
                    MethodElement mixinMethod = i.next();
                    AnnotationMetadataHierarchy metadata = new AnnotationMetadataHierarchy(mixinMethod, mixin);
                    Predicate<String> predicate = createPredicate(metadata);
                    String[] annotationsToRemove = findAnnotationsToRemove(metadata);
                    if (readMethod != null) {
                        if (mixinMethod.getName().equals(readMethod.getName())) {
                            if (argumentsMatch(mixinMethod, readMethod)) {
                                i.remove();
                                copyAnnotations(mixinMethod, targetProperty, predicate, annotationsToRemove);
                                copyAnnotations(mixinMethod, readMethod, predicate);
                            }
                        }
                    }
                    if (writeMethod != null) {
                        if (mixinMethod.getName().equals(writeMethod.getName())) {
                            if (argumentsMatch(mixinMethod, writeMethod)) {
                                i.remove();
                                copyAnnotations(mixinMethod, targetProperty, predicate, annotationsToRemove);
                                copyAnnotations(mixinMethod, writeMethod, predicate);
                            }
                        }
                    }
                }
            }
        }

        for (FieldElement mixinField : mixinFields.values()) {
            mixinTarget.getFields().stream()
                .filter(f -> f.getName().equals(mixinField.getName()))
                .findFirst()
                .ifPresent(targetField -> {
                    AnnotationMetadataHierarchy metadata = new AnnotationMetadataHierarchy(mixinField, mixin);
                    copyAnnotations(
                        mixinField,
                        targetField,
                        createPredicate(metadata),
                        findAnnotationsToRemove(metadata)
                    );
                });
        }

        for (MethodElement mixinMethod : mixinMethods) {
            mixinTarget.getEnclosedElement(
                ElementQuery.ALL_METHODS
                    .onlyInstance()
                    .onlyAccessible()
                    .named(mixinMethod.getName())
                    .filter(method -> method.getReturnType().equals(mixinMethod.getReturnType()) && argumentsMatch(method, mixinMethod))
            ).ifPresent(targetMethod -> copyAnnotations(mixinMethod, targetMethod, createPredicate(new AnnotationMetadataHierarchy(mixinMethod, mixin))));
        }

        if (mixin.hasAnnotation(ClassImport.class)) {
            visitorContext.warn("Mixin shouldn't be used with @ClassImport", mixin);
        }
        mixin.removeAnnotationIf(annotationValue -> true);
        mixin.annotate(Vetoed.class);
    }

    private static Predicate<String> createPredicate(AnnotationMetadata element) {
        AnnotationValue<Mixin.Filter> filterAnnotation = element.getAnnotation(Mixin.Filter.class);
        if (filterAnnotation == null) {
            return name -> true;
        }
        String[] includeAnnotations = filterAnnotation.stringValues("includeAnnotations");
        String[] excludeAnnotations = filterAnnotation.stringValues("excludeAnnotations");
        return annotationName -> {
            if (includeAnnotations.length != 0) {
                for (String filter : includeAnnotations) {
                    if (annotationName.startsWith(filter)) {
                        return true;
                    }
                }
                return false;
            }
            if (excludeAnnotations.length != 0) {
                for (String filter : excludeAnnotations) {
                    if (annotationName.startsWith(filter)) {
                        return false;
                    }
                }
                return true;
            }
            return true;
        };
    }

    private static boolean argumentsMatch(MethodElement left, MethodElement right) {
        final ParameterElement[] lp = left.getParameters();
        final ParameterElement[] rp = right.getParameters();
        if (lp.length == rp.length) {
            if (lp.length == 0) {
                return true;
            }
            for (int i = 0; i < lp.length; i++) {
                ParameterElement p1 = lp[i];
                ParameterElement p2 = rp[i];
                if (!p1.getType().equals(p2.getType())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static void copyAnnotations(MutableAnnotationMetadataDelegate<?> source,
                                        io.micronaut.inject.ast.Element target,
                                        Predicate<String> annotationPredicate,
                                        String[] removeAnnotations) {
        for (String removeAnnotation : removeAnnotations) {
            for (String annotationName : target.getAnnotationNames()) {
                if (annotationName.startsWith(removeAnnotation)) {
                    target.removeAnnotation(annotationName);
                }
            }
        }
        final Set<String> annotationNames = source.getAnnotationNames();
        for (String annotationName : annotationNames) {
            if (Mixin.Filter.class.getName().equals(annotationName) || Mixin.class.getName().equals(annotationName)) {
                continue;
            }
            if (annotationPredicate.test(annotationName)) {
                AnnotationValue<?> annotation = source.getAnnotation(annotationName);
                if (annotation == null) {
                    continue;
                }
                target.annotate(annotation);
            }
        }
    }

    private static String[] findAnnotationsToRemove(AnnotationMetadata source) {
        AnnotationValue<Mixin.Filter> filter = source.getAnnotation(Mixin.Filter.class);
        if (filter != null) {
            return filter.stringValues("removeAnnotations");
        }
        return new String[0];
    }

    private static void copyAnnotations(MethodElement source,
                                        MethodElement target,
                                        Predicate<String> annotationPredicate) {
        copyAnnotations(source.getMethodAnnotationMetadata(), target, annotationPredicate, findAnnotationsToRemove(source));
        ParameterElement[] sourceParameters = source.getParameters();
        ParameterElement[] targetParameters = target.getParameters();
        for (int i = 0; i < sourceParameters.length; i++) {
            ParameterElement sourceParameter = sourceParameters[i];
            ParameterElement targetParameter = targetParameters[i];
            copyAnnotations(
                sourceParameter,
                targetParameter,
                annotationPredicate,
                findAnnotationsToRemove(new AnnotationMetadataHierarchy(source, targetParameter))
            );
        }
    }

}
