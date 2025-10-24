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
package io.micronaut.python.processing.visitor;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.expressions.context.ExpressionCompilationContextFactory;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;
import io.micronaut.python.processing.annotation.PythonAnnotationMetadataBuilder;
import io.micronaut.python.processing.annotation.PythonElementAnnotationMetadataFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PythonVisitorContext implements VisitorContext {
    private final MutableConvertibleValues<Object> visitorAttributes = new MutableConvertibleValuesMap<>();
    private final Map<String, DecoratorDef> decorators;

    public PythonVisitorContext(Map<String, DecoratorDef> decorators) {
        this.decorators = decorators;
    }

    @Override
    public Language getLanguage() {
        return Language.PYTHON;
    }

    @Override
    public ElementFactory<?, ?, ?, ?> getElementFactory() {
        return null;
    }

    @Override
    public PythonElementAnnotationMetadataFactory getElementAnnotationMetadataFactory() {
        return new PythonElementAnnotationMetadataFactory(
            false,
            getAnnotationMetadataBuilder()
        );
    }

    @Override
    public ExpressionCompilationContextFactory getExpressionCompilationContextFactory() {
        throw new UnsupportedOperationException("Expressions not yet supported");
    }

    @Override
    public PythonAnnotationMetadataBuilder getAnnotationMetadataBuilder() {
        return new PythonAnnotationMetadataBuilder(decorators, this);
    }

    @Override
    public void info(String message, Element element) {
        System.out.println("INFO: " + message + " @ " + element);
    }

    @Override
    public void info(String message) {
        System.out.println("INFO: " + message);
    }

    @Override
    public void fail(String message, Element element) {
        System.err.println("ERROR: " + message + " @ " + element);
    }

    @Override
    public void warn(String message, Element element) {
        System.out.println("WARN: " + message);
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements) {
        return Optional.empty();
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        return Optional.empty();
    }

    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, Object value) {
        return visitorAttributes.put(key, value);
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        return visitorAttributes.remove(key);
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        return visitorAttributes.clear();
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

    @Override
    public OutputStream visitClass(String classname, Element... originatingElements) throws IOException {
        return null;
    }

    @Override
    public void visitServiceDescriptor(String type, String classname) {

    }

    @Override
    public void visitServiceDescriptor(String type, String classname, Element originatingElement) {

    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path, Element... originatingElements) {
        return Optional.empty();
    }

    @Override
    public void finish() {

    }
}
