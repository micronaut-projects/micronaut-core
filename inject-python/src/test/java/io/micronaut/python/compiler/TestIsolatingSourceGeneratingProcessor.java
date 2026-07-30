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
package io.micronaut.python.compiler;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

final class TestIsolatingSourceGeneratingProcessor extends AbstractProcessor {
    private boolean generated;

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of("org.gradle.annotation.processing.isolating");
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(TestIsolate.class.getName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        if (generated || annotations.isEmpty()) {
            return false;
        }
        for (Element element : roundEnvironment.getElementsAnnotatedWith(TestIsolate.class)) {
            try {
                JavaFileObject source = processingEnv.getFiler()
                    .createSourceFile("generated.UnrelatedGenerated", element);
                try (Writer writer = source.openWriter()) {
                    writer.write("""
                        package generated;
                        public final class UnrelatedGenerated {}
                        """);
                }
                generated = true;
                break;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return false;
    }
}
