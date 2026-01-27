/*
 * Copyright 2017-2026 original authors
 */
package io.micronaut.python.processing;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonScriptElement;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import org.graalvm.polyglot.Value;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PythonPooledStubGenerator {
    static final TypeDef POLYGLOT_VALUE = TypeDef.of(Value.class);
    static final ClassTypeDef RUNTIME_UTIL = ClassTypeDef.of("io.micronaut.context.python.GraalPyRuntimeUtil");
    static final ClassTypeDef CONTEXT_HOLDER = ClassTypeDef.of("io.micronaut.context.python.ContextHolder");
    static final String AS_POLYGLOT_VALUE = "asPolyglotValue";
    static final String FROM_POLYGLOT_VALUE = "fromPolyglotValue";

    static ClassDef.ClassDefBuilder generatePooledClass(AbstractPythonClassElement element,
                                                        VisitorContext context,
                                                        Map<String, ClassElement> allClasses) {
        String typeName = element.getName();
        var builder = ClassDef.builder(typeName).addModifiers(Modifier.PUBLIC);
        builder.addAnnotation(Vetoed.class);
        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));

        ClassElement superType = element.getSuperType().orElse(null);
        boolean extendsPythonClass = superType instanceof AbstractPythonClassElement;
        if (extendsPythonClass) {
            builder.superclass(ClassTypeDef.of(superType.getName()));
        }

        List<PropertyElement> beanProperties = element.getBeanProperties();
        if (!beanProperties.isEmpty()) {
            throw new ProcessingException(element, "@Pooled does not support introspected bean properties on Python classes.");
        }
        var pythonConstructor = element.getPrimaryConstructor().orElse(null);
        if (pythonConstructor != null && pythonConstructor.getParameters().length > 0) {
            throw new ProcessingException(element, "@Pooled types must be stateless. Constructor with parameters is not supported.");
        }

        MethodDef.MethodDefBuilder ctor = MethodDef.constructor();
        builder.addMethod(ctor.build(((aThis, params) -> StatementDef.multi())));

        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC)
            .returns(POLYGLOT_VALUE)
            .build(((aThis, params) -> CONTEXT_HOLDER
                .invokeStatic("getPooled", POLYGLOT_VALUE, List.of(
                    ExpressionDef.constant(element.getPackageName()),
                    ExpressionDef.constant(element.getSimpleName())
                )).returning())));

        ClassTypeDef thisType = ClassTypeDef.of(typeName);
        builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(POLYGLOT_VALUE)
            .returns(thisType)
            .build(((aThis, methodParameters) -> thisType.instantiate().returning())));

        List<MethodElement> methodsToBridge = element.getEnclosedElements(
            ElementQuery.ALL_METHODS
                .onlyAccessible()
                .onlyInstance()
                .onlyDeclared()
                .annotated(ann -> ann.hasStereotype(Around.class)
                    || ann.hasStereotype(AnnotationUtil.SCOPE)
                    || ann.hasDeclaredStereotype(Bean.class)
                    || ann.hasStereotype("io.micronaut.context.annotation.Executable"))
        );

        for (MethodElement methodElement : methodsToBridge) {
            addBridgeMethodPooledClass(methodElement, builder, element, allClasses);
        }

        return builder;
    }

    static ClassDef.ClassDefBuilder generatePooledScript(PythonScriptElement scriptElement,
                                                         VisitorContext context,
                                                         Map<String, ClassElement> allClasses) {
        String typeName = scriptElement.getName();
        var builder = ClassDef.builder(scriptElement.getPackageName() + "." + scriptElement.getSimpleName())
            .addModifiers(Modifier.PUBLIC);
        builder.addAnnotation(Vetoed.class);
        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));

        MethodDef.MethodDefBuilder ctor = MethodDef.constructor();
        builder.addMethod(ctor.build(((aThis, params) -> StatementDef.multi())));

        ClassTypeDef thisType = ClassTypeDef.of(typeName);
        String name = scriptElement.getNativeType().name();
        if (name.endsWith(".py")) {
            name = name.substring(0, name.length() - 3);
        }

        String pkg = scriptElement.getPackageName();
        String script = name;

        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC)
            .returns(POLYGLOT_VALUE)
            .build(((aThis, params) -> CONTEXT_HOLDER
                .invokeStatic("getPooledScript", POLYGLOT_VALUE,
                    List.of(ExpressionDef.constant(pkg), ExpressionDef.constant(script)))
                .returning())));

        builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(POLYGLOT_VALUE)
            .returns(thisType)
            .build(((aThis, methodParameters) -> thisType.instantiate().returning())));

        List<MethodElement> methodsToBridge = scriptElement.getEnclosedElements(
            ElementQuery.ALL_METHODS
                .onlyAccessible()
                .onlyInstance()
                .onlyDeclared()
                .annotated(ann -> ann.hasStereotype(Around.class)
                    || ann.hasStereotype(AnnotationUtil.SCOPE)
                    || ann.hasDeclaredStereotype(Bean.class)
                    || ann.hasStereotype("io.micronaut.context.annotation.Executable"))
        );

        for (MethodElement methodElement : methodsToBridge) {
            addBridgeMethodPooledScript(methodElement, builder, pkg, script, allClasses);
        }

        List<PropertyElement> beanProperties = scriptElement.getBeanProperties();
        for (PropertyElement beanProperty : beanProperties) {
            if (beanProperty.hasStereotype(AnnotationUtil.INJECT)) {
                addSetterScriptPooled(beanProperty, builder, pkg, script);
            }
            if (beanProperty.hasStereotype(Bean.class)) {
                addGetterScriptPooled(beanProperty, builder, pkg, script, allClasses);
            }
        }

        return builder;
    }

    private static void addBridgeMethodPooledClass(MethodElement methodElement,
                                                   ClassDef.ClassDefBuilder builder,
                                                   AbstractPythonClassElement element,
                                                   Map<String, ClassElement> allClasses) {
        String pythonFunctionName = methodElement.getName();
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.of(methodElement.getReturnType()));

        for (ParameterElement parameter : methodElement.getParameters()) {
            var parameterType = erasedType(parameter.getType());
            methodBuilder.addParameter(ParameterDef.builder(parameter.getName(), parameterType).build());
        }

        builder.addMethod(methodBuilder.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameterExpressions = new ArrayList<>();
            for (int i = 0; i < methodElement.getParameters().length; i++) {
                ParameterElement parameter = methodElement.getParameters()[i];
                VariableDef.MethodParameter mp = methodParameters.get(i);
                coerceParameterToPolyglotValue(parameter, parameterExpressions, mp);
            }
            List<ExpressionDef> args = new ArrayList<>();
            args.add(ExpressionDef.constant(element.getPackageName()));
            args.add(ExpressionDef.constant(element.getSimpleName()));
            args.add(ExpressionDef.constant(pythonFunctionName));
            args.addAll(parameterExpressions);
            var invoked = CONTEXT_HOLDER.invokeStatic("invokePooled", POLYGLOT_VALUE, args);
            return handleReturnType(methodElement.getReturnType(), invoked, allClasses);
        })));

    }

    private static void addBridgeMethodPooledScript(MethodElement methodElement,
                                                    ClassDef.ClassDefBuilder builder,
                                                    String pkg,
                                                    String script,
                                                    Map<String, ClassElement> allClasses) {
        String pythonFunctionName = methodElement.getName();
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.of(methodElement.getReturnType()));

        for (ParameterElement parameter : methodElement.getParameters()) {
            var parameterType = erasedType(parameter.getType());
            methodBuilder.addParameter(ParameterDef.builder(parameter.getName(), parameterType).build());
        }

        builder.addMethod(methodBuilder.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameterExpressions = new ArrayList<>();
            for (int i = 0; i < methodElement.getParameters().length; i++) {
                ParameterElement parameter = methodElement.getParameters()[i];
                VariableDef.MethodParameter mp = methodParameters.get(i);
                coerceParameterToPolyglotValue(parameter, parameterExpressions, mp);
            }
            List<ExpressionDef> args = new ArrayList<>();
            args.add(ExpressionDef.constant(pkg));
            args.add(ExpressionDef.constant(script));
            args.add(ExpressionDef.constant(pythonFunctionName));
            args.addAll(parameterExpressions);
            var invoked = CONTEXT_HOLDER.invokeStatic("invokePooledScript", POLYGLOT_VALUE, args);
            return handleReturnType(methodElement.getReturnType(), invoked, allClasses);
        })));

    }

    private static void addGetterScriptPooled(PropertyElement beanProperty,
                                              ClassDef.ClassDefBuilder builder,
                                              String pkg,
                                              String script,
                                              Map<String, ClassElement> allClasses) {
        TypeDef propertyType = TypeDef.of(beanProperty.getType());
        String getterName = beanProperty.getReadMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> {
            var invoked = CONTEXT_HOLDER.invokeStatic("invokePooledScript", POLYGLOT_VALUE,
                List.of(ExpressionDef.constant(pkg), ExpressionDef.constant(script), ExpressionDef.constant(beanProperty.getName())));
            return handleReturnType(beanProperty.getType(), invoked, allClasses);
        })));
    }

    private static void addSetterScriptPooled(PropertyElement beanProperty,
                                              ClassDef.ClassDefBuilder builder,
                                              String pkg,
                                              String script) {
        TypeDef returnType = beanProperty.getWriteMethod().map(MethodElement::getReturnType).map(TypeDef::of).orElse(TypeDef.VOID);
        String setterName = beanProperty.getWriteMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        propertySetter.addParameter(TypeDef.of(beanProperty.getType()));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(pkg));
            parameters.add(ExpressionDef.constant(script));
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            coerceParameterToPolyglotValue(beanProperty, parameters, methodParameters.getFirst());
            var result = CONTEXT_HOLDER.invokeStatic("injectedPooledScript", TypeDef.VOID, parameters);
            if (returnType.equals(TypeDef.VOID)) {
                return result;
            } else {
                return StatementDef.multi(result, ExpressionDef.nullValue().returning());
            }
        })));
    }

    private static TypeDef erasedType(ClassElement t) {
        return !t.getTypeArguments().isEmpty() ? ClassTypeDef.of(t.getName()) : TypeDef.of(t);
    }

    private static void coerceParameterToPolyglotValue(io.micronaut.inject.ast.TypedElement param,
                                                       List<ExpressionDef> parameters,
                                                       VariableDef.MethodParameter methodParam) {
        ClassElement genericType = param.getGenericType();
        if (genericType.isAssignable(Map.class) && genericType.getTypeArguments().get("V") instanceof PythonClassElement) {
            parameters.add(RUNTIME_UTIL.invokeStatic("coerceMap", TypeDef.of(Map.class), methodParam));
        } else if (genericType.isAssignable(List.class) && genericType.getTypeArguments().get("E") instanceof PythonClassElement) {
            parameters.add(RUNTIME_UTIL.invokeStatic("coerceList", TypeDef.of(List.class), methodParam));
        } else if (genericType instanceof PythonClassElement) {
            if (param.hasAnnotation("jakarta.annotation.Nullable")) {
                parameters.add(methodParam.isNull().doIfElse(ExpressionDef.nullValue(), methodParam.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE)));
            } else {
                parameters.add(methodParam.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE));
            }
        } else {
            parameters.add(methodParam);
        }
    }

    private static StatementDef handleReturnType(ClassElement returnType,
                                                 ExpressionDef valueExpr,
                                                 Map<String, ClassElement> allClasses) {
        if (returnType.isVoid()) {
            return valueExpr.returning();
        } else if (returnType.isPrimitive()) {
            return switch (returnType.getName()) {
                case "int", "java.lang.Integer" -> valueExpr.invoke("asInt", TypeDef.Primitive.INT).returning();
                case "boolean", "java.lang.Boolean" -> valueExpr.invoke("asBoolean", TypeDef.Primitive.BOOLEAN).returning();
                case "double", "java.lang.Double" -> valueExpr.invoke("asDouble", TypeDef.Primitive.DOUBLE).returning();
                case "float", "java.lang.Float" -> valueExpr.invoke("asFloat", TypeDef.Primitive.FLOAT).returning();
                case "long", "java.lang.Long" -> valueExpr.invoke("asLong", TypeDef.Primitive.LONG).returning();
                case "short", "java.lang.Short" -> valueExpr.invoke("asShort", TypeDef.Primitive.SHORT).returning();
                case "byte", "java.lang.Byte" -> valueExpr.invoke("asByte", TypeDef.Primitive.BYTE).returning();
                case "char", "java.lang.Character" -> valueExpr.invoke("asString", ClassTypeDef.STRING).invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)).returning();
                default -> valueExpr.invoke("asString", ClassTypeDef.STRING).returning();
            };
        } else {
            String referenceTypeName = returnType.getName();
            switch (referenceTypeName) {
                case "java.lang.Integer":
                    return valueExpr.invoke("asInt", TypeDef.Primitive.INT).returning();
                case "java.lang.Boolean":
                    return valueExpr.invoke("asBoolean", TypeDef.Primitive.BOOLEAN).returning();
                case "java.lang.Double":
                    return valueExpr.invoke("asDouble", TypeDef.Primitive.DOUBLE).returning();
                case "java.lang.Float":
                    return valueExpr.invoke("asFloat", TypeDef.Primitive.FLOAT).returning();
                case "java.lang.Long":
                    return valueExpr.invoke("asLong", TypeDef.Primitive.LONG).returning();
                case "java.lang.Short":
                    return valueExpr.invoke("asShort", TypeDef.Primitive.SHORT).returning();
                case "java.lang.Byte":
                    return valueExpr.invoke("asByte", TypeDef.Primitive.BYTE).returning();
                case "java.lang.Character":
                    return valueExpr.invoke("asString", ClassTypeDef.STRING).invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)).returning();
                case "java.lang.String":
                    return valueExpr.invoke("asString", ClassTypeDef.STRING).returning();
                default:
                    if (returnType.isAssignable(List.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return RUNTIME_UTIL.invokeStatic("convertList", ClassTypeDef.of(List.class), valueExpr, genericType).returning();
                    } else if (returnType.isAssignable(Map.class)) {
                        Map<String, ClassElement> typeArguments = returnType.getTypeArguments();
                        ExpressionDef keyType = toClassExpression(typeArguments.get("K"));
                        ExpressionDef valueType = toClassExpression(typeArguments.get("V"));
                        return RUNTIME_UTIL.invokeStatic("convertMap", ClassTypeDef.of(Map.class), valueExpr, keyType, valueType).returning();
                    } else if (returnType.isAssignable(Set.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return RUNTIME_UTIL.invokeStatic("convertSet", ClassTypeDef.of(Set.class), valueExpr, genericType).returning();
                    } else if (returnType.isAssignable(java.util.Optional.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return RUNTIME_UTIL.invokeStatic("convertOptional", ClassTypeDef.of(java.util.Optional.class), valueExpr, genericType).returning();
                    } else {
                        if (allClasses.containsKey(returnType.getName())) {
                            return ClassTypeDef.of(returnType).invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, valueExpr).returning();
                        } else {
                            return RUNTIME_UTIL.invokeStatic("convertValue", ClassTypeDef.OBJECT, valueExpr, ClassTypeDef.of(returnType.getRawClassElement().getName()).getStaticField("class", TypeDef.CLASS)).cast(TypeDef.of(returnType)).returning();
                        }
                    }
            }
        }
    }

    private static ExpressionDef toClassExpression(ClassElement componentType) {
        if (componentType == null) {
            return ClassTypeDef.of(Object.class).getStaticField("class", TypeDef.CLASS);
        } else {
            return ClassTypeDef.of(componentType).getStaticField("class", TypeDef.CLASS);
        }
    }
}
