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
