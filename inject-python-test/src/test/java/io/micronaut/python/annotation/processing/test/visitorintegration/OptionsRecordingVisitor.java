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
package io.micronaut.python.annotation.processing.test.visitorintegration;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records the visitor context options seen while visiting classes annotated with {@link RecordOptions}.
 */
public class OptionsRecordingVisitor implements TypeElementVisitor<RecordOptions, Object> {

    private static final Map<String, String> OPTIONS = Collections.synchronizedMap(new LinkedHashMap<>());

    public static Map<String, String> recordedOptions() {
        return new LinkedHashMap<>(OPTIONS);
    }

    public static void reset() {
        OPTIONS.clear();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        OPTIONS.putAll(context.getOptions());
    }
}
