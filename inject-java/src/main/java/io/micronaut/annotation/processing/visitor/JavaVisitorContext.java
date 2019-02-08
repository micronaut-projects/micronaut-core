/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.annotation.processing.AnnotationProcessingOutputVisitor;
import io.micronaut.annotation.processing.AnnotationUtils;
import io.micronaut.annotation.processing.ModelUtils;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import javax.annotation.Nullable;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * The visitor context when visiting Java code.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class JavaVisitorContext implements VisitorContext {

    private final Messager messager;
    private final Elements elements;
    private final AnnotationUtils annotationUtils;
    private final Types types;
    private final ModelUtils modelUtils;
    private final AnnotationProcessingOutputVisitor outputVisitor;
    private final MutableConvertibleValues<Object> visitorAttributes;

    /**
     * The default constructor.
     * @param messager The messager
     * @param elements The elements
     * @param annotationUtils The annotation utils
     * @param types Type types
     * @param modelUtils The model utils
     * @param filer The filer
     * @param visitorAttributes The attributes
     */
    public JavaVisitorContext(
            Messager messager,
            Elements elements,
            AnnotationUtils annotationUtils,
            Types types,
            ModelUtils modelUtils,
            Filer filer,
            MutableConvertibleValues<Object> visitorAttributes) {
        this.messager = messager;
        this.elements = elements;
        this.annotationUtils = annotationUtils;
        this.types = types;
        this.modelUtils = modelUtils;
        this.outputVisitor = new AnnotationProcessingOutputVisitor(filer);
        this.visitorAttributes = visitorAttributes;
    }

    @Override
    public Optional<ClassElement> getClassElement(String name) {
        TypeElement typeElement = elements.getTypeElement(name);
        return Optional.ofNullable(typeElement).map(typeElement1 ->
                new JavaClassElement(typeElement1, annotationUtils.getAnnotationMetadata(typeElement1), this, Collections.emptyList())
        );
    }

    @Override
    public void info(String message, @Nullable io.micronaut.inject.ast.Element element) {
        printMessage(message, Diagnostic.Kind.NOTE, element);
    }

    @Override
    public void info(String message) {
        if (StringUtils.isNotEmpty(message)) {
            messager.printMessage(Diagnostic.Kind.NOTE, message);
        }
    }

    @Override
    public void fail(String message, @Nullable io.micronaut.inject.ast.Element element) {
        printMessage(message, Diagnostic.Kind.ERROR, element);
    }

    @Override
    public void warn(String message, @Nullable io.micronaut.inject.ast.Element element) {
        printMessage(message, Diagnostic.Kind.WARNING, element);
    }

    private void printMessage(String message, Diagnostic.Kind kind, @Nullable io.micronaut.inject.ast.Element element) {
        if (StringUtils.isNotEmpty(message)) {
            if (element != null) {
                Element el = (Element) element.getNativeType();
                messager.printMessage(kind, message, el);
            } else {
                messager.printMessage(kind, message);
            }
        }
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path) {
        return outputVisitor.visitMetaInfFile(path);
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        return outputVisitor.visitGeneratedFile(path);
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

    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, @Nullable Object value) {
        visitorAttributes.put(key, value);
        return this;
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        visitorAttributes.remove(key);
        return this;
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        visitorAttributes.clear();
        return this;
    }

    @Override
    public Set<String> names() {
        return visitorAttributes.names();
    }

    @Override
    public Collection<Object> values() {
        return visitorAttributes.values();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return visitorAttributes.get(name, conversionContext);
    }
}
