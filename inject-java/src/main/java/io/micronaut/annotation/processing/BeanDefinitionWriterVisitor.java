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
package io.micronaut.annotation.processing;

import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

public class BeanDefinitionWriterVisitor implements ClassWriterOutputVisitor {

    private final File targetDirectory;
    private final Filer filer;

    BeanDefinitionWriterVisitor(Filer filer,  File targetDirectory) {
        this.targetDirectory = targetDirectory;
        this.filer = filer;
    }

    @Override
    public OutputStream visitClass(String classname) throws IOException {
        JavaFileObject javaFileObject = filer.createClassFile(classname);
        return javaFileObject.openOutputStream();
    }

    @Override
    public Optional<File> visitServiceDescriptor(String classname) throws IOException {
        return Optional.ofNullable(targetDirectory).map(root ->
            new File(root, SoftServiceLoader.META_INF_SERVICES + File.separator + classname)
        );
    }

    @Override
    public Optional<File> visitMetaInfFile(String path) throws IOException {
        return Optional.ofNullable(targetDirectory).map(root ->
                new File(root, "META-INF" + File.separator + path)
        );
    }
}
