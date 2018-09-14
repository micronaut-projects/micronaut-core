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

package io.micronaut.annotation.processing.visitor;

import io.micronaut.annotation.processing.AnnotationUtils;
import io.micronaut.annotation.processing.ModelUtils;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * The visitor context when visiting Java code.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class JavaVisitorContext implements VisitorContext {

    private final Messager messager;
    private final Elements elements;
    private final AnnotationUtils annotationUtils;
    private final Types types;
    private final ModelUtils modelUtils;

    /**
     * The default constructor.
     *
     * @param messager The messager
     * @param elements The elements
     * @param annotationUtils The annotation utils
     * @param types Type types
     * @param modelUtils The model utils
     */
    public JavaVisitorContext(Messager messager, Elements elements, AnnotationUtils annotationUtils, Types types, ModelUtils modelUtils) {
        this.messager = messager;
        this.elements = elements;
        this.annotationUtils = annotationUtils;
        this.types = types;
        this.modelUtils = modelUtils;
    }

    @Override
    public void fail(String message, io.micronaut.inject.visitor.Element element) {
        Element el = (Element) element.getNativeType();
        messager.printMessage(Diagnostic.Kind.ERROR, message, el);
    }

    @Override
    public void warn(String message, io.micronaut.inject.visitor.Element element) {
        Element el = (Element) element.getNativeType();
        messager.printMessage(Diagnostic.Kind.WARNING, message, el);
    }

    /**
     * The messager.
     *
     * @return The messager
     */
    public Messager getMessager() {
        return messager;
    }

    /**
     * The model utils.
     *
     * @return The model utils
     */
    public ModelUtils getModelUtils() {
        return modelUtils;
    }

    /**
     * The elements.
     *
     * @return The elements
     */
    public Elements getElements() {
        return elements;
    }

    /**
     * The annotation utils.
     *
     * @return The annotation utils
     */
    public AnnotationUtils getAnnotationUtils() {
        return annotationUtils;
    }

    /**
     * The types.
     *
     * @return The types
     */
    public Types getTypes() {
        return types;
    }
}
