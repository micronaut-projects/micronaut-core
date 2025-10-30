package io.micronaut.python.processing;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

public class PythonStubGenerator implements TypeElementVisitor<Object, Object> {

    public static final TypeDef POLYGLOT_VALUE = TypeDef.of(Value.class);

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element instanceof AbstractPythonClassElement) {
            SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(VisitorContext.Language.JAVA).orElse(null);
            if (sourceGenerator != null) {
                try {
                    String typeName = element.getName();
                    var builder = ClassDef.builder(typeName);
                    FieldDef pythonValue = FieldDef.builder("graalpyInternalValue")
                        .ofType(POLYGLOT_VALUE).addModifiers(Modifier.FINAL, Modifier.PRIVATE).build();
                    builder.addField(pythonValue);
                    // add constructor that looks up value
                    MethodDef.MethodDefBuilder constructor = MethodDef.constructor();
                    builder.addMethod(
                        constructor.build(((aThis, methodParameters) -> aThis.field(pythonValue).assign(
                            ClassTypeDef.of("io.micronaut.context.python.ContextHolder")
                                .invokeStatic("getContext", TypeDef.of(Context.class))
                                .invoke("getBindings", POLYGLOT_VALUE, ExpressionDef.constant("python"))
                                .invoke("getMember", POLYGLOT_VALUE, ExpressionDef.constant(element.getSimpleName()))
                                .invoke("newInstance", POLYGLOT_VALUE)
                        )))
                    );

                    List<MethodElement> methodsToBridge = element.getEnclosedElements(
                        ElementQuery.ALL_METHODS
                            .onlyAccessible()
                            .onlyInstance()
                            .annotated(ann -> ann.hasStereotype(Executable.class)));


                    for (MethodElement methodElement : methodsToBridge) {
                        String pythonFunctionName = methodElement.getName();
                        String javaName = NameUtils.camelCase(pythonFunctionName);
                        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(javaName)
                            .returns(TypeDef.of(methodElement.getReturnType()));

                        for (@NonNull ParameterElement parameter : methodElement.getParameters()) {
                            var parameterType = TypeDef.of(parameter.getType());
                            ParameterDef parameterDef = ParameterDef
                                .builder(parameter.getName(), parameterType).build();
                            methodBuilder.addParameter(parameterDef);
                        }

                        builder.addMethod(methodBuilder
                            .build(((aThis, methodParameters) -> {
                                VariableDef.Field pythonValueField = aThis.field(pythonValue);
                                List<ExpressionDef> parameters = new ArrayList<>();
                                parameters.add(ExpressionDef.constant(pythonFunctionName));
                                parameters.addAll(methodParameters);

                                // Get the return type to determine appropriate conversion method
                                var returnType = methodElement.getReturnType();
                                var invokedValue = pythonValueField.invoke(
                                    "invokeMember",
                                    POLYGLOT_VALUE,
                                    parameters
                                );

                                // Choose appropriate conversion method based on return type
                                if (returnType.isPrimitive()) {
                                    String primitiveTypeName = returnType.getName();
                                    return switch (primitiveTypeName) {
                                        case "int" ->
                                            invokedValue.invoke("asInt", TypeDef.Primitive.INT).returning();
                                        case "boolean" ->
                                            invokedValue.invoke("asBoolean", TypeDef.Primitive.BOOLEAN).returning();
                                        case "double" ->
                                            invokedValue.invoke("asDouble", TypeDef.Primitive.DOUBLE).returning();
                                        case "float" ->
                                            invokedValue.invoke("asFloat", TypeDef.Primitive.FLOAT).returning();
                                        case "long" ->
                                            invokedValue.invoke("asLong", TypeDef.Primitive.LONG).returning();
                                        case "short" ->
                                            invokedValue.invoke("asShort", TypeDef.Primitive.SHORT).returning();
                                        case "byte" ->
                                            invokedValue.invoke("asByte", TypeDef.Primitive.BYTE).returning();
                                        case "char" ->
                                            invokedValue.invoke("asString", ClassTypeDef.STRING)
                                                .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)).returning();
                                        default ->
                                            invokedValue.invoke("asString", ClassTypeDef.STRING).returning();
                                    };
                                } else {
                                    // Handle boxed types and other reference types
                                    String referenceTypeName = returnType.getName();
                                    return switch (referenceTypeName) {
                                        case "java.lang.Integer" ->
                                            invokedValue.invoke("asInt", TypeDef.Primitive.INT).returning();
                                        case "java.lang.Boolean" ->
                                            invokedValue.invoke("asBoolean", TypeDef.Primitive.BOOLEAN).returning();
                                        case "java.lang.Double" ->
                                            invokedValue.invoke("asDouble", TypeDef.Primitive.DOUBLE).returning();
                                        case "java.lang.Float" ->
                                            invokedValue.invoke("asFloat", TypeDef.Primitive.FLOAT).returning();
                                        case "java.lang.Long" ->
                                            invokedValue.invoke("asLong", TypeDef.Primitive.LONG).returning();
                                        case "java.lang.Short" ->
                                            invokedValue.invoke("asShort", TypeDef.Primitive.SHORT).returning();
                                        case "java.lang.Byte" ->
                                            invokedValue.invoke("asByte", TypeDef.Primitive.BYTE).returning();
                                        case "java.lang.Character" ->
                                            invokedValue.invoke("asString", ClassTypeDef.STRING)
                                                .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)).returning();
                                        case "java.lang.String" ->
                                            invokedValue.invoke("asString", ClassTypeDef.STRING).returning();
                                        default ->
                                            // For complex types (List, Map, etc.) or unknown types, convert to string for now
                                            // TODO: Add proper handling for collections and complex types
                                            invokedValue.invoke("asString", ClassTypeDef.STRING).returning();
                                    };
                                }
                            })));
                    }

                    sourceGenerator.write(builder.build(), context, element);
                } catch (Exception e) {
                    context.fail("Failed to generate stub for Python type [" + element.getSimpleName() + "]: " + e.getMessage(), null);
                }

            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
