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

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.processing.JavacFiler;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Options;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

abstract class AbstractInjectAnnotationProcessor extends AbstractProcessor {

    protected Messager messager;
    protected Filer filer;
    protected Elements elementUtils;
    protected Types typeUtils;
    protected AnnotationUtils annotationUtils;
    protected GenericUtils genericUtils;
    protected ModelUtils modelUtils;
    private File targetDirectory;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.modelUtils = new ModelUtils(elementUtils,typeUtils);
        this.annotationUtils = new AnnotationUtils(elementUtils);
        this.genericUtils = new GenericUtils(elementUtils,typeUtils, modelUtils);


        URI baseDir = null;
        try {
            baseDir = filer.getResource(StandardLocation.CLASS_OUTPUT, "", "").toUri();
        } catch (Exception e) {
            // ignore
        }
        if(baseDir == null) {
            // OpenJDK doesn't like resolving root so we have to use a dummy sub-directory. Very hacky this.
            try {
                URI uri = filer.getResource(StandardLocation.CLASS_OUTPUT, "", "-root").toUri();
                if(uri != null) {
                    File parentFile = new File(uri).getParentFile();
                    File canonicalFile = parentFile.getCanonicalFile();
                    if(canonicalFile.exists()) {
                        baseDir = canonicalFile.toURI();
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if(baseDir != null) {
            try {
                this.targetDirectory = new File(baseDir);
            } catch (Exception e) {
                // ignore
            }
        }
        if(targetDirectory == null) {
            try {
                Options javacOptions = Options.instance(((JavacProcessingEnvironment)processingEnv).getContext());
                String javacDirectoryOption = javacOptions.get(Option.D);
                if(javacDirectoryOption != null) {

                    this.targetDirectory = new File(javacDirectoryOption);
                }
            } catch (Exception e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "No base directory for compilation of Java sources could be established");
            }
        }
    }

    public Optional<File> getTargetDirectory() {
        return Optional.ofNullable(targetDirectory);
    }

    // error will produce a "compile error"
    protected void error(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }

    // error will produce a "compile error"
    protected void error(String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args));
    }

    protected void warning(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.WARNING, String.format(msg, args), e);
    }

    protected void warning(String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.WARNING, String.format(msg, args));
    }

    protected void note(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args), e);
    }

    protected void note(String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args));
    }
}
