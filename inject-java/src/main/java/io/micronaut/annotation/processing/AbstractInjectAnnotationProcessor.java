/*
 * Copyright 2017-2018 original authors
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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Abstract annotation processor base class.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractInjectAnnotationProcessor extends AbstractProcessor {

    protected Messager messager;
    protected Filer filer;
    protected Elements elementUtils;
    protected Types typeUtils;
    protected AnnotationUtils annotationUtils;
    protected GenericUtils genericUtils;
    protected ModelUtils modelUtils;
    protected ClassWriterOutputVisitor classWriterOutputVisitor;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.classWriterOutputVisitor = new AnnotationProcessingOutputVisitor(filer);
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.modelUtils = new ModelUtils(elementUtils, typeUtils);
        this.annotationUtils = new AnnotationUtils(elementUtils);
        this.genericUtils = new GenericUtils(elementUtils, typeUtils, modelUtils);
    }

    /**
     * Produce a compile error for the given element and message.
     *
     * @param e    The element
     * @param msg  The message
     * @param args The string format args
     */
    protected final void error(Element e, String msg, Object... args) {
        if (messager == null) {
            illegalState();
            return;
        }
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }

    /**
     * Produce a compile error for the given message.
     *
     * @param msg  The message
     * @param args The string format args
     */
    protected final void error(String msg, Object... args) {
        if (messager == null) {
            illegalState();
        }
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args));
    }

    /**
     * Produce a compile warning for the given element and message.
     *
     * @param e    The element
     * @param msg  The message
     * @param args The string format args
     */
    protected final void warning(Element e, String msg, Object... args) {
        if (messager == null) {
            illegalState();
        }
        messager.printMessage(Diagnostic.Kind.WARNING, String.format(msg, args), e);
    }

    /**
     * Produce a compile warning for the given message.
     *
     * @param msg  The message
     * @param args The string format args
     */
    @SuppressWarnings("WeakerAccess")
    protected final void warning(String msg, Object... args) {
        if (messager == null) {
            illegalState();
        }
        messager.printMessage(Diagnostic.Kind.WARNING, String.format(msg, args));
    }

    /**
     * Produce a compile note for the given element and message.
     *
     * @param e    The element
     * @param msg  The message
     * @param args The string format args
     */
    protected final void note(Element e, String msg, Object... args) {
        if (messager == null) {
            illegalState();
        }
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args), e);
    }

    /**
     * Produce a compile note for the given element and message.
     *
     * @param msg  The message
     * @param args The string format args
     */
    protected final void note(String msg, Object... args) {
        if (messager == null) {
            illegalState();
        }
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args));
    }

    private void illegalState() {
        throw new IllegalStateException("No messager set. Ensure processing enviroment is initialized");
    }
}
