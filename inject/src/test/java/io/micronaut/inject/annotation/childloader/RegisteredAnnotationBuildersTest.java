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
package io.micronaut.inject.annotation.childloader;

import io.micronaut.core.annotation.AnnotationBuilder;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.RegisteredAnnotationBuilders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An annotation whose type is in the class loader of Micronaut and whose builder is registered in an application
 * class loader below it.
 */
class RegisteredAnnotationBuildersTest {

    @Test
    void theBuilderOfAnApplicationLoaderBuildsTheAnnotation(@TempDir Path services) throws Exception {
        String builder = ChildMarkBuilders$io_micronaut_inject_annotation_childloader_ChildMark$AnnotationBuilder.class.getName();
        Path entry = services.resolve("META-INF/micronaut/" + AnnotationBuilder.class.getName() + "/" + builder);
        Files.createDirectories(entry.getParent());
        Files.createFile(entry);
        AnnotationValue<ChildMark> value = AnnotationValue.builder(ChildMark.class).value("child").build();

        assertTrue(AnnotationBuilder.find(ChildMark.class).isEmpty());
        assertThrows(IllegalStateException.class, () -> RegisteredAnnotationBuilders.build(ChildMark.class, value, null));
        try (URLClassLoader application = new URLClassLoader(new URL[] {services.toUri().toURL()}, getClass().getClassLoader())) {
            assertEquals("child", RegisteredAnnotationBuilders.build(ChildMark.class, value, application).value());
        }
    }
}
