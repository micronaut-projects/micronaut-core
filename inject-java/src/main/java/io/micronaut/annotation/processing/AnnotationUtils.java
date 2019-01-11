/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for annotations.
 *
 * @author Graeme Rocher
 * @author Dean Wette
 */
@SuppressWarnings("ConstantName")
public class AnnotationUtils {

    private static final int CACHE_SIZE = 100;
    private static final Map<Element, AnnotationMetadata> annotationMetadataCache = new ConcurrentLinkedHashMap.Builder<Element, AnnotationMetadata>().maximumWeightedCapacity(CACHE_SIZE).build();

    private final Elements elementUtils;
    private final Messager messager;
    private final Types types;
    private final ModelUtils modelUtils;
    private final Filer filer;
    private final MutableConvertibleValues<Object> visitorAttributes;

    /**
     * Default constructor.
     *
     * @param elementUtils      The elements
     * @param messager          The messager
     * @param types             The types
     * @param modelUtils        The model utils
     * @param filer             The filer
     * @param visitorAttributes The visitor attributes
     */
    protected AnnotationUtils(
            Elements elementUtils,
            Messager messager,
            Types types,
            ModelUtils modelUtils,
            Filer filer,
            MutableConvertibleValues<Object> visitorAttributes) {
        this.elementUtils = elementUtils;
        this.messager = messager;
        this.types = types;
        this.modelUtils = modelUtils;
        this.filer = filer;
        this.visitorAttributes = visitorAttributes;
    }

    /**
     * Default constructor.
     *
     * @param elementUtils      The elements
     * @param messager          The messager
     * @param types             The types
     * @param modelUtils        The model utils
     * @param filer             The filer
     */
    protected AnnotationUtils(
            Elements elementUtils,
            Messager messager,
            Types types,
            ModelUtils modelUtils,
            Filer filer) {
        this(elementUtils, messager, types, modelUtils, filer, new MutableConvertibleValuesMap<>());
    }

    /**
     * Return whether the given element is annotated with the given annotation stereotype.
     *
     * @param element    The element
     * @param stereotype The stereotype
     * @return True if it is
     */
    protected boolean hasStereotype(Element element, Class<? extends Annotation> stereotype) {
        return hasStereotype(element, stereotype.getName());
    }

    /**
     * Return whether the given element is annotated with the given annotation stereotypes.
     *
     * @param element     The element
     * @param stereotypes The stereotypes
     * @return True if it is
     */
    protected boolean hasStereotype(Element element, String... stereotypes) {
        return hasStereotype(element, Arrays.asList(stereotypes));
    }

    /**
     * Return whether the given element is annotated with any of the given annotation stereotypes.
     *
     * @param element     The element
     * @param stereotypes The stereotypes
     * @return True if it is
     */
    protected boolean hasStereotype(Element element, List<String> stereotypes) {
        if (element == null) {
            return false;
        }
        if (stereotypes.contains(element.toString())) {
            return true;
        }
        AnnotationMetadata annotationMetadata = getAnnotationMetadata(element);
        for (String stereotype : stereotypes) {
            if (annotationMetadata.hasStereotype(stereotype)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the annotation metadata for the given element.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata getAnnotationMetadata(Element element) {
        AnnotationMetadata metadata = annotationMetadataCache.get(element);
        if (metadata == null) {
            metadata = newAnnotationBuilder().build(element);
            annotationMetadataCache.put(element, metadata);
        }
        return metadata;
    }

    /**
     * Get the annotation metadata for the given element.
     *
     * @param parent  The parent
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata getAnnotationMetadata(Element parent, Element element) {
        return newAnnotationBuilder().buildForParent(parent, element);
    }

    /**
     * Check whether the method is annotated.
     *
     * @param method The method
     * @return True if it is annotated with non internal annotations
     */
    public boolean isAnnotated(ExecutableElement method) {
        List<? extends AnnotationMirror> annotationMirrors = method.getAnnotationMirrors();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            String typeName = annotationMirror.getAnnotationType().toString();
            if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(typeName)) {
                return true;
            }
        }
        return false;
    }

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
                types,
                modelUtils,
                filer
        );
    }

    /**
     * Creates a new {@link JavaVisitorContext}.
     *
     * @return The visitor context
     */
    public JavaVisitorContext newVisitorContext() {
        return new JavaVisitorContext(
                messager,
                elementUtils,
                this,
                types,
                modelUtils,
                filer,
                visitorAttributes
        );
    }

    /**
     * Invalidates any cached metadata.
     */
    @Internal
    static void invalidateCache() {
        annotationMetadataCache.clear();
    }

}
