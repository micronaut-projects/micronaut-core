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
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.Set;

@SupportedAnnotationTypes("*")
@SupportedOptions("org.gradle.annotation.processing.isolating")
final class TestContractViolatingProcessor extends AbstractProcessor {
    private boolean generated;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (generated || roundEnv.processingOver()) {
            return false;
        }
        generated = true;
        try {
            processingEnv.getFiler()
                .createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/contract-violation.txt",
                    roundEnv.getRootElements().toArray(javax.lang.model.element.Element[]::new)
                )
                .openWriter()
                .close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return false;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
