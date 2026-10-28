/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import spock.lang.Specification

class AnnotationMetadataLookupOverridesSpec extends Specification {

    void "hasAnnotation sees an annotation a subclass answers from the declared lookup"() {
        given:
        def metadata = new DefaultAnnotationMetadata(null, null, null, null, null) {
            @Override
            boolean hasDeclaredAnnotation(String annotation) {
                return annotation == "test.Foo" || super.hasDeclaredAnnotation(annotation)
            }
        }

        expect:
        metadata.hasAnnotation("test.Foo")
        !metadata.hasAnnotation("test.Bar")
    }

    void "repeatable values a subclass adds while asked for the container are found"() {
        given:
        AnnotationMetadataSupport.registerRepeatableAnnotation("test.LazyTag", "test.LazyTags")
        Map<String, Map<CharSequence, Object>> annotations = new HashMap<>()
        def metadata = new DefaultAnnotationMetadata(annotations, null, null, annotations, null) {
            @Override
            boolean hasStereotype(String annotation) {
                if (annotation == "test.LazyTags" && !annotations.containsKey(annotation)) {
                    annotations.put(annotation, [(AnnotationMetadata.VALUE_MEMBER): [AnnotationValue.builder("test.LazyTag").value("x").build()] as AnnotationValue[]])
                }
                return super.hasStereotype(annotation)
            }
        }

        expect:
        metadata.getAnnotationValuesByName("test.LazyTag").size() == 1
    }

    void "a hierarchy merges a layer's sequential list by iterating it"() {
        given:
        def first = new DefaultAnnotationMetadata(null, null, null, null, ["test.Meta": ["test.A", "test.B"]])
        def sequential = new LinkedList<String>(["test.B", "test.C"]) {
            @Override
            String get(int index) {
                throw new UnsupportedOperationException("indexed read of a sequential list")
            }
        }
        def second = new DefaultAnnotationMetadata(null, null, null, null, null) {
            @Override
            List<String> getAnnotationNamesByStereotype(String stereotype) {
                return stereotype == "test.Meta" ? sequential : super.getAnnotationNamesByStereotype(stereotype)
            }
        }

        expect:
        new AnnotationMetadataHierarchy(first, second).getAnnotationNamesByStereotype("test.Meta") == ["test.B", "test.C", "test.A"]
    }
}
