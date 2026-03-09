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
package io.micronaut.aop.compile;

import io.micronaut.annotation.processing.test.Parser;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.StreamSupport;

/**
 * Java port of GeneratedAnnotationSpec.
 */
class GeneratedAnnotationTest extends AbstractTypeElementTest {

    @Test
    void testOnlyOneGeneratedAnnotationIsAdded() throws IOException {
        Iterable<? extends JavaFileObject> files = Parser.generate("example.FooController", """
            package example;

            import io.micronaut.http.annotation.Controller;
            import io.micronaut.http.annotation.Get;
            import io.micronaut.validation.Validated;

                    @Validated
                    @Controller("/")
                    class FooController {

                        @Get
                        String foo() {
                            return "";
                        }
                    }
            """);
        JavaFileObject f = StreamSupport.stream(files.spliterator(), false)
            .filter(it -> it.getName().contains("FooController" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX + ".class"))
            .findFirst().orElseThrow();
        try (InputStream is = f.openInputStream()) {
            ClassReader reader = new ClassReader(is.readAllBytes());
            final int[] generatedAnnotations = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (descriptor.contains("Generated")) {
                        generatedAnnotations[0]++;
                    }
                    return super.visitAnnotation(descriptor, visible);
                }
            }, ClassReader.SKIP_CODE);

            // "Only one generated annotation is added"
            Assertions.assertEquals(1, generatedAnnotations[0]);
        }
    }

}
