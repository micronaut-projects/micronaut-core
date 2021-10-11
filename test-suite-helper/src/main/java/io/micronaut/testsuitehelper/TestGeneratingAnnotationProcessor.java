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
package io.micronaut.testsuitehelper;

import static javax.lang.model.SourceVersion.RELEASE_8;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(RELEASE_8)
public class TestGeneratingAnnotationProcessor extends AbstractProcessor {

    private boolean executed = false;

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (executed) {
            return false;
        }

        try {
            final String output = determineOutputPath();
            final File outputDir = new File(output);

            switch (outputDir.getName()) {
                case "main":
                case "classes":
                    break;
                case "test":
                case "test-classes":

                    try (Writer w = processingEnv.getFiler()
                        .createSourceFile("io.micronaut.test.generated.Example")
                        .openWriter()) {
                        w.write("package io.micronaut.test.generated;\n\npublic interface Example {}");
                    }

                    try (Writer w = processingEnv.getFiler()
                        .createSourceFile("io.micronaut.test.generated.IntrospectedExample")
                        .openWriter()) {
                        w.write("package io.micronaut.test.generated;\n\nimport io.micronaut.core.annotation.Introspected;\n\n@Introspected\npublic class IntrospectedExample {}");
                    }

                    break;
                default:
                    throw new IllegalStateException("Unknown builder for output " + outputDir);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        executed = true;
        return false;
    }

    private String determineOutputPath() throws IOException {
        // go write a file so as to figure out where we're running
        final FileObject resource = processingEnv
            .getFiler()
            .createResource(StandardLocation.CLASS_OUTPUT, "", "tmp" + System.currentTimeMillis(), (Element[]) null);
        try {
            return new File(resource.toUri()).getCanonicalFile().getParent();
        } finally {
            resource.delete();
        }
    }

}
