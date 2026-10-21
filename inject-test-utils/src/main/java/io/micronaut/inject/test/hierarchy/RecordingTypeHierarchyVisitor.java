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
package io.micronaut.inject.test.hierarchy;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.TypeHierarchyImport;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.TypeHierarchyRecord;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the hierarchies that {@link TypeHierarchyImport} asks for a second time, through
 * {@link VisitorContext#tryRecordTypeHierarchy(ClassElement, io.micronaut.inject.ast.Element...)}, and keeps what the
 * API returned by the name of the record, for a test to compare with the record it loads. A class of the annotated
 * class that cannot be recorded is kept as empty.
 */
public class RecordingTypeHierarchyVisitor implements TypeElementVisitor<TypeHierarchyImport, Object> {

    /**
     * What the API returned, by the name of the record.
     */
    public static final Map<String, TypeHierarchyRecord> RECORDS = new ConcurrentHashMap<>();

    /**
     * The types refused, by the name of the annotated class then the name of the type, as {@code probe.Hierarchies#p.Refused}.
     */
    public static final Map<String, Optional<TypeHierarchyRecord>> REFUSED = new ConcurrentHashMap<>();

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        AnnotationValue<TypeHierarchyImport> annotation = element.getAnnotation(TypeHierarchyImport.class);
        if (annotation == null) {
            return;
        }
        for (AnnotationClassValue<?> classValue : annotation.annotationClassValues("classes")) {
            context.getClassElement(classValue.getName()).ifPresent(type -> {
                Optional<TypeHierarchyRecord> record = context.tryRecordTypeHierarchy(type, element);
                if (record.isPresent()) {
                    RECORDS.put(record.get().getClassName(), record.get());
                } else {
                    REFUSED.put(element.getName() + "#" + type.getName(), record);
                }
            });
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
