package io.micronaut.python.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Modifier;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

public class PythonStubGenerator implements TypeElementVisitor<Object, Object> {

    public static final TypeDef POLYGLOT_VALUE = TypeDef.of(Value.class);
    public static final VariableDef.StaticField CLASS_OBJECT = ClassTypeDef.of(Object.class).getStaticField("class", TypeDef.CLASS);

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
                    builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));

                    FieldDef pythonValue = FieldDef.builder("graalpyInternalValue")
                        .ofType(POLYGLOT_VALUE).addModifiers(Modifier.FINAL, Modifier.PRIVATE).build();
                    builder.addField(pythonValue);

                    // implement asPolygotValue
                    builder.addMethod(MethodDef.builder("asPolyglotValue")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(POLYGLOT_VALUE).build(((aThis, methodParameters) -> aThis.field(pythonValue).returning())));

                    // Check if there's a primary constructor with parameters for dependency injection
                    var pythonConstructor = element.getPrimaryConstructor().orElse(null);

                    if (pythonConstructor != null && pythonConstructor.getParameters().length > 0) {
                        // Generate constructor with dependency injection parameters
                        MethodDef.MethodDefBuilder constructor = MethodDef.constructor();
                        @NonNull ParameterElement[] parameters = pythonConstructor.getParameters();
                        for (@NonNull ParameterElement parameter : parameters) {
                            var parameterType = TypeDef.of(parameter.getType());
                            ParameterDef parameterDef = ParameterDef
                                .builder(parameter.getName(), parameterType).build();
                            constructor.addParameter(parameterDef);
                        }

                        builder.addMethod(
                            constructor.build(((aThis, methodParameters) -> {
                                // Create the Python object by calling constructor with parameters
                                ExpressionDef pythonClass = ClassTypeDef.of("io.micronaut.context.python.ContextHolder")
                                    .invokeStatic("getContext", TypeDef.of(Context.class))
                                    .invoke("getBindings", POLYGLOT_VALUE, ExpressionDef.constant("python"))
                                    .invoke("getMember", POLYGLOT_VALUE, ExpressionDef.constant(element.getSimpleName()));

                                List<ExpressionDef> arguments = new ArrayList<>();
                                for (int i = 0; i < parameters.length; i++) {
                                    @NonNull ParameterElement parameter = parameters[i];
                                    VariableDef.MethodParameter methodParameter = methodParameters.get(i);
                                    if (parameter.getType() instanceof PythonClassElement) {
                                        arguments.add(methodParameter.invoke("asPolyglotValue", POLYGLOT_VALUE));
                                    } else {
                                        arguments.add(methodParameter);
                                    }
                                }
                                // Pass constructor parameters directly to newInstance
                                ExpressionDef pythonInstance = pythonClass.invoke("newInstance", POLYGLOT_VALUE, arguments);

                                // Assign to field
                                return aThis.field(pythonValue).assign(pythonInstance);
                            }))
                        );
                    } else {
                        // Default constructor for classes without __init__ or with no parameters
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
                    }

                    List<MethodElement> methodsToBridge = element.getEnclosedElements(
                        ElementQuery.ALL_METHODS
                            .onlyAccessible()
                            .onlyInstance()
                            .annotated(ann -> ann.hasStereotype(Executable.class)));


                    for (MethodElement methodElement : methodsToBridge) {
                        String pythonFunctionName = methodElement.getName();
                        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
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

                                return handlReturnType(returnType, invokedValue);
                            })));
                    }

                    // Find injection methods (annotated with @Inject)
                    List<MethodElement> injectionMethods = element.getEnclosedElements(
                        ElementQuery.ALL_METHODS
                            .onlyAccessible()
                            .onlyInstance()
                            .annotated(ann -> ann.hasStereotype("jakarta.inject.Inject")));

                    // Generate methods for injection
                    for (MethodElement injectionMethod : injectionMethods) {
                        MethodDef.MethodDefBuilder injectionMethodBuilder = MethodDef.builder(injectionMethod.getName());
                        if (!injectionMethod.getReturnType().isVoid()) {
                            injectionMethodBuilder.returns(TypeDef.of(injectionMethod.getReturnType()));
                        }

                        for (@NonNull ParameterElement parameter : injectionMethod.getParameters()) {
                            var parameterType = TypeDef.of(parameter.getType());
                            ParameterDef parameterDef = ParameterDef
                                .builder(parameter.getName(), parameterType).build();
                            injectionMethodBuilder.addParameter(parameterDef);
                        }

                        builder.addMethod(injectionMethodBuilder
                            .build(((aThis, methodParameters) -> {
                                VariableDef.Field pythonValueField = aThis.field(pythonValue);
                                List<ExpressionDef> parameters = new ArrayList<>();
                                parameters.add(ExpressionDef.constant(injectionMethod.getName()));

                                // Handle parameter conversion for Python classes
                                for (int i = 0; i < injectionMethod.getParameters().length; i++) {
                                    ParameterElement param = injectionMethod.getParameters()[i];
                                    VariableDef.MethodParameter methodParam = methodParameters.get(i);
                                    if (param.getType() instanceof PythonClassElement) {
                                        parameters.add(methodParam.invoke("asPolyglotValue", POLYGLOT_VALUE));
                                    } else {
                                        parameters.add(methodParam);
                                    }
                                }

                                // Call the Python injection method
                                var invokedValue = pythonValueField.invoke(
                                    "invokeMember",
                                    POLYGLOT_VALUE,
                                    parameters
                                );

                                // For injection methods, just invoke without explicit return
                                ClassElement returnType = injectionMethod.getReturnType();
                                if (returnType.isVoid()) {
                                    return invokedValue;
                                } else {
                                    return StatementDef.multi(
                                        invokedValue,
                                        ExpressionDef.nullValue().returning()
                                    );
                                }
                            })));
                    }

                    // Find injection fields (with Annotated[Type, Inject] syntax)
                    // For now, we'll look for fields that have any annotation and check for Inject in metadata
                    List<PropertyElement> beanProperties = element.getBeanProperties();
                    for (PropertyElement beanProperty : beanProperties) {
                        if (beanProperty.hasStereotype(AnnotationUtil.INJECT)) {
                            MethodDef.MethodDefBuilder propertySetter = MethodDef.builder(beanProperty.getName())
                                .returns(TypeDef.VOID);

                            propertySetter.addParameter(TypeDef.of(beanProperty.getType()));

                            builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
                                VariableDef.Field pythonValueField = aThis.field(pythonValue);
                                ExpressionDef param;
                                if (beanProperty.getType() instanceof PythonClassElement) {
                                    param = methodParameters.get(0).invoke("asPolyglotValue", POLYGLOT_VALUE);
                                } else {
                                    param = methodParameters.get(0);
                                }
                                // Call the Python injection method
                                return pythonValueField.invoke(
                                    "putMember",
                                    TypeDef.VOID,
                                    ExpressionDef.constant(beanProperty.getName()),
                                    param
                                );
                            })));
                        }
                    }

                    sourceGenerator.write(builder.build(), context, element);
                } catch (Exception e) {
                    context.fail("Failed to generate stub for Python type [" + element.getSimpleName() + "]: " + e.getMessage(), null);
                }

            }
        }
    }

    private static StatementDef handlReturnType(ClassElement returnType, ExpressionDef.InvokeInstanceMethod invokedValue) {
        // Choose appropriate conversion method based on return type
        if (returnType.isVoid()) {
            // For void methods, just invoke the Python method without returning
            return invokedValue;
        } else if (returnType.isPrimitive()) {
            return convertPrimitive(returnType, invokedValue);
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
                case "java.lang.Character" -> invokedValue.invoke("asString", ClassTypeDef.STRING)
                    .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)).returning();
                case "java.lang.String" ->
                    invokedValue.invoke("asString", ClassTypeDef.STRING).returning();
                default -> {
                    // Check for collection types
                    if (returnType.isAssignable(List.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        yield ClassTypeDef.of("io.micronaut.context.python.GraalPyRuntimeUtil")
                            .invokeStatic("convertList", ClassTypeDef.of(List.class),
                                invokedValue, genericType)
                            .returning();
                    } else if (returnType.isAssignable(Map.class)) {
                        Map<String, ClassElement> typeArguments = returnType.getTypeArguments();
                        ExpressionDef keyType = toClassExpression(typeArguments.get("K"));
                        ExpressionDef valueType = toClassExpression(typeArguments.get("V"));
                        yield ClassTypeDef.of("io.micronaut.context.python.GraalPyRuntimeUtil")
                            .invokeStatic("convertMap", ClassTypeDef.of(Map.class),
                                invokedValue, keyType, valueType)
                            .returning();
                    } else if (returnType.isAssignable(Set.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield ClassTypeDef.of("io.micronaut.context.python.GraalPyRuntimeUtil")
                            .invokeStatic("convertSet", ClassTypeDef.of(Set.class),
                                invokedValue, genericType)
                            .returning();
                    } else if (returnType.isAssignable(java.util.Optional.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield ClassTypeDef.of("io.micronaut.context.python.GraalPyRuntimeUtil")
                            .invokeStatic("convertOptional", ClassTypeDef.of(java.util.Optional.class),
                                invokedValue, genericType)
                            .returning();
                    } else {
                        // For unknown types, convert to string as fallback
                        yield invokedValue.invoke("asString", ClassTypeDef.STRING).returning();
                    }
                }
            };
        }
    }

    private static ExpressionDef toClassExpression(ClassElement componentType) {
        ExpressionDef genericType;
        if (componentType == null) {
            genericType = CLASS_OBJECT;
        } else {
            genericType = ClassTypeDef.of(componentType).getStaticField("class", TypeDef.CLASS);
        }
        return genericType;
    }

    private static StatementDef convertPrimitive(ClassElement returnType, ExpressionDef.InvokeInstanceMethod invokedValue) {
        String primitiveTypeName = returnType.getName();
        return switch (primitiveTypeName) {
            case "int", "java.lang.Integer" ->
                invokedValue.invoke("asInt", TypeDef.Primitive.INT).returning();
            case "boolean", "java.lang.Boolean" ->
                invokedValue.invoke("asBoolean", TypeDef.Primitive.BOOLEAN).returning();
            case "double", "java.lang.Double" ->
                invokedValue.invoke("asDouble", TypeDef.Primitive.DOUBLE).returning();
            case "float", "java.lang.Float" ->
                invokedValue.invoke("asFloat", TypeDef.Primitive.FLOAT).returning();
            case "long", "java.lang.Long" ->
                invokedValue.invoke("asLong", TypeDef.Primitive.LONG).returning();
            case "short", "java.lang.Short" ->
                invokedValue.invoke("asShort", TypeDef.Primitive.SHORT).returning();
            case "byte", "java.lang.Byte" ->
                invokedValue.invoke("asByte", TypeDef.Primitive.BYTE).returning();
            case "char", "java.lang.Character" ->
                invokedValue.invoke("asString", ClassTypeDef.STRING)
                    .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)).returning();
            default -> invokedValue.invoke("asString", ClassTypeDef.STRING).returning();
        };
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
