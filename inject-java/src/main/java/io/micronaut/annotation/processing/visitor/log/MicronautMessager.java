/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.annotation.processing.visitor.log;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import io.micronaut.core.annotation.Internal;

import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;

/**
 * Custom implementation of the Messager built on top of log. This is copy od standard
 * com.sun.tools.javac.processing.JavacMessager, but with correct PrintWriter for
 * NOTICE messages.
 *
 * @since 4.0.0
 */
@Internal
public final class MicronautMessager implements Messager {

    Log log;
    Elements javacElements;
    int errorCount;
    int warningCount;

    public MicronautMessager(ProcessingEnvironment processingEnv) {
        log = Log.instance();
        javacElements = processingEnv.getElementUtils();
    }

    @Override
    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public void printMessage(Diagnostic.Kind kind, CharSequence msg) {
        printMessage(kind, msg, null, null, null);
    }

    @Override
    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e) {
        printMessage(kind, msg, e, null, null);
    }

    /**
     * Prints a message of the specified kind at the location of the
     * annotation mirror of the annotated element.
     *
     * @param kind the kind of message
     * @param msg the message, or an empty string if none
     * @param e the annotated element
     * @param a the annotation to use as a position hint
     */
    @Override
    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a) {
        printMessage(kind, msg, e, a, null);
    }

    /**
     * Prints a message of the specified kind at the location of the
     * annotation value inside the annotation mirror of the annotated
     * element.
     *
     * @param kind the kind of message
     * @param msg the message, or an empty string if none
     * @param e the annotated element
     * @param a the annotation containing the annotation value
     * @param v the annotation value to use as a position hint
     */
    @Override
    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a, AnnotationValue v) {
        JavaFileObject oldSource = null;
        JavaFileObject newSource = null;
        switch (kind) {
            case ERROR -> {
                errorCount++;
                log.error(Log.NOPOS, msg.toString());
            }
            case MANDATORY_WARNING, WARNING -> {
                warningCount++;
                log.warning(Log.NOPOS, msg.toString());
            }
            default -> log.note(Log.NOPOS, msg.toString());
        }
    }

    /**
     * Prints an error message.
     * Equivalent to {@code printError(null, msg)}.
     *
     * @param msg the message, or an empty string if none
     */
    public void printError(String msg) {
        printMessage(Diagnostic.Kind.ERROR, msg);
    }

    /**
     * Prints a warning message.
     * Equivalent to {@code printWarning(null, msg)}.
     *
     * @param msg the message, or an empty string if none
     */
    public void printWarning(String msg) {
        printMessage(Diagnostic.Kind.WARNING, msg);
    }

    /**
     * Prints a notice.
     *
     * @param msg the message, or an empty string if none
     */
    public void printNotice(String msg) {
        printMessage(Diagnostic.Kind.NOTE, msg);
    }

    public boolean errorRaised() {
        return errorCount > 0;
    }

    public int errorCount() {
        return errorCount;
    }

    public int warningCount() {
        return warningCount;
    }

    public void newRound() {
        errorCount = 0;
    }

    @Override
    public String toString() {
        return "Micronaut Messager";
    }
}
