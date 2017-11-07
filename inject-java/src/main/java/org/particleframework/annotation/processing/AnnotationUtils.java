/*
 * Copyright 2017 original authors
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
package org.particleframework.annotation.processing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.inject.annotation.JavaAnnotationMetadataBuilder;

import javax.inject.Qualifier;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * Utility methods for annotations
 *
 * @author Graeme Rocher
 * @author Dean Wette
 */
class AnnotationUtils {

    private static final Cache<Element, AnnotationMetadata> annotationMetadataCache = Caffeine.newBuilder().maximumSize(100).build();

    private final Elements elementUtils;

    AnnotationUtils(Elements elementUtils) {
        this.elementUtils = elementUtils;
    }

    /**
     * Return whether the given element is annotated with the given annotation stereotype
     *
     * @param element The element
     * @param stereotype The stereotype
     * @return True if it is
     */
    boolean hasStereotype(Element element, Class<? extends Annotation> stereotype) {
        return hasStereotype(element, stereotype.getName());
    }

    /**
     * Return whether the given element is annotated with the given annotation stereotypes
     *
     * @param element The element
     * @param stereotypes The stereotypes
     * @return True if it is
     */
    boolean hasStereotype(Element element, String... stereotypes) {
        return hasStereotype(element, Arrays.asList(stereotypes));
    }

    /**
     * Return whether the given element is annotated with any of the given annotation stereotypes
     *
     * @param element The element
     * @param stereotypes The stereotypes
     * @return True if it is
     */
    boolean hasStereotype(Element element, List<String> stereotypes) {
        if (element == null) {
            return false;
        }
        if (stereotypes.contains(element.toString())) {
            return true;
        }
        AnnotationMetadata annotationMetadata = getAnnotationMetadata(element);
        for (String stereotype : stereotypes) {
            if(annotationMetadata.hasStereotype(stereotype)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the annotation metadata for the given element
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata getAnnotationMetadata(Element element) {
        return annotationMetadataCache.get(element, element1 -> new JavaAnnotationMetadataBuilder(elementUtils).build(element1));
    }


    /**
     * Resolve the {@link Qualifier} to use for the given element
     * @param element The element
     * @return The Qualifier or null
     */
    String resolveQualifier(Element element) {
        return getAnnotationMetadata(element).getAnnotationByStereotype(Qualifier.class).orElse(null);
    }


}
