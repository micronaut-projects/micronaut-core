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

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.inject.annotation.AnnotatedElementValidator;
import io.micronaut.inject.visitor.TypeElementVisitor;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility methods for annotations.
 *
 * @author Graeme Rocher
 * @author Dean Wette
 */
@SuppressWarnings("ConstantName")
@Internal
public class AnnotationUtils {

    private static final int CACHE_SIZE = 100;
    private static final Map<Key, AnnotationMetadata> annotationMetadataCache
            = new ConcurrentLinkedHashMap.Builder<Key, AnnotationMetadata>().maximumWeightedCapacity(CACHE_SIZE).build();

    private final Elements elementUtils;
    private final Messager messager;
    private final Types types;
    private final ModelUtils modelUtils;
    private final Filer filer;
    private final MutableConvertibleValues<Object> visitorAttributes;
    private final ProcessingEnvironment processingEnv;
    private final AnnotatedElementValidator elementValidator;
    private JavaAnnotationMetadataBuilder javaAnnotationMetadataBuilder;
    private final GenericUtils genericUtils;

    /**
     * Default constructor.
     *
     * @param processingEnv     The processing env
     * @param elementUtils      The elements
     * @param messager          The messager
     * @param types             The types
     * @param modelUtils        The model utils
     * @param genericUtils      The generic utils
     * @param filer             The filer
     * @param visitorAttributes The visitor attributes
     */
    protected AnnotationUtils(
            ProcessingEnvironment processingEnv,
            Elements elementUtils,
            Messager messager,
            Types types,
            ModelUtils modelUtils,
            GenericUtils genericUtils,
            Filer filer,
            MutableConvertibleValues<Object> visitorAttributes) {
        this.elementUtils = elementUtils;
        this.messager = messager;
        this.types = types;
        this.modelUtils = modelUtils;
        this.genericUtils = genericUtils;
        this.filer = filer;
        this.visitorAttributes = visitorAttributes;
        this.processingEnv = processingEnv;
        this.elementValidator = SoftServiceLoader.load(AnnotatedElementValidator.class).firstAvailable().orElse(null);
        this.javaAnnotationMetadataBuilder = newAnnotationBuilder();
    }

    /**
     * Default constructor.
     *
     * @param processingEnv     The processing env
     * @param elementUtils      The elements
     * @param messager          The messager
     * @param types             The types
     * @param modelUtils        The model utils
     * @param genericUtils      The generic utils
     * @param filer             The filer
     */
    protected AnnotationUtils(
            ProcessingEnvironment processingEnv,
            Elements elementUtils,
            Messager messager,
            Types types,
            ModelUtils modelUtils,
            GenericUtils genericUtils,
            Filer filer) {
        this(processingEnv, elementUtils, messager, types, modelUtils, genericUtils, filer, new MutableConvertibleValuesMap<>());
    }

    /**
     * The {@link AnnotatedElementValidator} instance. Can be null.
     * @return The validator instance
     */
    public @Nullable AnnotatedElementValidator getElementValidator() {
        return elementValidator;
    }

    /**
     * Get the annotation metadata for the given element.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata getAnnotationMetadata(TypeElement classElement, Element element) {
        return getCachedAnnotationMetadata(classElement, element);
    }

    public AnnotationMetadata getAnnotationMetadata(TypeElement classElement) {
        return getCachedAnnotationMetadata(classElement, classElement);
    }

    public AnnotationMetadata getAnnotationMetadata(PackageElement packageElement) {
        return getCachedAnnotationMetadata(packageElement, packageElement);
    }

    /**
     * Get the annotation metadata for the given element.
     *
     * @param owningType The owningType
     * @param element    The element
     * @return The {@link AnnotationMetadata}
     */
    private AnnotationMetadata getCachedAnnotationMetadata(Element owningType, Element element) {
        Key key = new Key(owningType, element);
        AnnotationMetadata metadata = annotationMetadataCache.get(key);
        if (metadata == null) {
            metadata = javaAnnotationMetadataBuilder.buildOverridden(owningType, element);
            annotationMetadataCache.put(key, metadata);
        }
        return metadata;
    }

    /**
     * Get the declared annotation metadata for the given element.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata getDeclaredAnnotationMetadata(Element element) {
        return javaAnnotationMetadataBuilder.buildDeclared(element);
    }

    /**
     * Get the annotation metadata for the given element and the given parent.
     * This method is used for cases when you need to combine annotation metadata for
     * two elements, for example a JavaBean property where the field and the method metadata
     * need to be combined.
     *
     * @param parent  The parent
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata getCombinedAnnotationMetadata(Element parent, Element element) {
        return newAnnotationBuilder().buildForParent(parent, element);
    }

    /**
     * Get the annotation metadata for the given element and the given parents.
     * This method is used for cases when you need to combine annotation metadata for
     * two elements, for example a JavaBean property where the field and the method metadata
     * need to be combined.
     *
     * @param parents The parents
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata getAnnotationMetadata(List<Element> parents, Element element) {
        return newAnnotationBuilder().buildForParents(parents, element);
    }

//    /**
//     * Check whether the method is annotated.
//     *
//     * @param declaringType The declaring type
//     * @param method The method
//     * @return True if it is annotated with non internal annotations
//     */
//    public boolean isAnnotated(String owningType, ExecutableElement method) {
//        if (AbstractAnnotationMetadataBuilder.isMetadataMutated(owningType, method)) {
//            return true;
//        }
//        List<? extends AnnotationMirror> annotationMirrors = method.getAnnotationMirrors();
//        for (AnnotationMirror annotationMirror : annotationMirrors) {
//            String typeName = annotationMirror.getAnnotationType().toString();
//            if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(typeName)) {
//                return true;
//            }
//        }
//        return false;
//    }

    /**
     * Creates a new annotation builder.
     *
     * @return The builder
     */
    public JavaAnnotationMetadataBuilder newAnnotationBuilder() {
        return new JavaAnnotationMetadataBuilder(
                elementUtils,
                messager,
                this,
                modelUtils
        );
    }

    /**
     * Creates a new {@link JavaVisitorContext}.
     *
     * @return The visitor context
     */
    public JavaVisitorContext newVisitorContext() {
        return new JavaVisitorContext(
                processingEnv,
                messager,
                elementUtils,
                this,
                types,
                modelUtils,
                genericUtils,
                filer,
                visitorAttributes,
                TypeElementVisitor.VisitorKind.ISOLATING
        );
    }

    /**
     * Invalidates any cached metadata.
     */
    @Internal
    static void invalidateCache() {
        annotationMetadataCache.clear();
    }

    /**
     * Invalidates any cached metadata.
     *
     * @param element The element
     */
    @Internal
    public static void invalidateMetadata(TypeElement owningType, Element element) {
        if (element != null) {
            annotationMetadataCache.remove(new Key(owningType, element));
        }
    }

    private static final class Key {
        private final Element owningType;
        private final Element element;
        private final int hashCode;

        private Key(Element owningType, Element element) {
            this.owningType = owningType;
            this.element = element;
            this.hashCode = Objects.hash(owningType, element);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key key = (Key) o;
            return hashCode == key.hashCode && Objects.equals(owningType, key.owningType) && Objects.equals(element, key.element);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
