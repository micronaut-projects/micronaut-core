/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.context.AbstractExecutableMethodsDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.KotlinParameterElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Switch based dispatch writer.
 *
 * @author Denis Stepanov
 * @since 4.7
 */
@Internal
public final class DispatchWriter implements ClassOutputWriter {

    private static final Method GET_ACCESSIBLE_TARGET_METHOD = ReflectionUtils.getRequiredInternalMethod(
        AbstractExecutableMethodsDefinition.class,
        "getAccessibleTargetMethodByIndex",
        int.class
    );

    private static final MethodDef UNKNOWN_DISPATCH_AT_INDEX = MethodDef.builder("unknownDispatchAtIndexException")
        .addParameter("index", int.class)
        .returns(RuntimeException.class)
        .build();

    private static final Method GET_TARGET_METHOD = ReflectionUtils.getRequiredInternalMethod(
        AbstractExecutableMethodsDefinition.class,
        "getTargetMethodByIndex",
        int.class
    );

    private static final Method DISPATCH_METHOD = ReflectionUtils.getRequiredInternalMethod(
        AbstractExecutableMethodsDefinition.class,
        "dispatch",
        int.class,
        Object.class,
        Object[].class
    );

    private static final String FIELD_INTERCEPTABLE = "$interceptable";

    private static final ClassTypeDef TYPE_REFLECTION_UTILS = ClassTypeDef.of(ReflectionUtils.class);

    private static final Method METHOD_GET_REQUIRED_METHOD = ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "getRequiredMethod", Class.class, String.class, Class[].class);

    private static final Method METHOD_INVOKE_METHOD = ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "invokeMethod", Object.class, java.lang.reflect.Method.class, Object[].class);

    private static final Method METHOD_GET_FIELD_VALUE =
        ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "getField", Class.class, String.class, Object.class);

    private static final Method METHOD_SET_FIELD_VALUE = ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "setField", Class.class, String.class, Object.class, Object.class);

    private final List<DispatchTarget> dispatchTargets = new ArrayList<>();

    private boolean hasInterceptedMethod;

    /**
     * Adds new set field dispatch target.
     *
     * @param beanField The field
     * @return the target index
     */
    public int addSetField(FieldElement beanField) {
        return addDispatchTarget(new FieldSetDispatchTarget(beanField));
    }

    /**
     * Adds new get field dispatch target.
     *
     * @param beanField The field
     * @return the target index
     */
    public int addGetField(FieldElement beanField) {
        return addDispatchTarget(new FieldGetDispatchTarget(beanField));
    }

    /**
     * Adds new method dispatch target.
     *
     * @param declaringType The declaring type
     * @param methodElement The method element
     * @return the target index
     */
    public int addMethod(TypedElement declaringType, MethodElement methodElement) {
        return addMethod(declaringType, methodElement, false);
    }

    /**
     * Adds new method dispatch target.
     *
     * @param declaringType  The declaring type
     * @param methodElement  The method element
     * @param useOneDispatch If method should be dispatched using "dispatchOne"
     * @return the target index
     */
    public int addMethod(TypedElement declaringType, MethodElement methodElement, boolean useOneDispatch) {
        DispatchTarget dispatchTarget = findDispatchTarget(declaringType, methodElement, useOneDispatch);
        return addDispatchTarget(dispatchTarget);
    }

    private DispatchTarget findDispatchTarget(TypedElement declaringType, MethodElement methodElement, boolean useOneDispatch) {
        List<ParameterElement> argumentTypes = Arrays.asList(methodElement.getSuspendParameters());
        boolean isKotlinDefault = argumentTypes.stream().anyMatch(p -> p instanceof KotlinParameterElement kp && kp.hasDefault());
        ClassElement declaringClassType = (ClassElement) declaringType;
        if (methodElement.isReflectionRequired()) {
            if (isKotlinDefault) {
                throw new ProcessingException(methodElement, "Kotlin default methods are not supported for reflection invocation");
            }
            return new MethodReflectionDispatchTarget(declaringType, methodElement, dispatchTargets.size(), useOneDispatch);
        } else if (isKotlinDefault) {
            return new KotlinMethodWithDefaultsDispatchTarget(declaringClassType, methodElement, useOneDispatch);
        }
        return new MethodDispatchTarget(declaringClassType, methodElement, useOneDispatch);
    }

    /**
     * Adds new interceptable method dispatch target.
     *
     * @param declaringType                    The declaring type
     * @param methodElement                    The method element
     * @param interceptedProxyClassName        The interceptedProxyClassName
     * @param interceptedProxyBridgeMethodName The interceptedProxyBridgeMethodName
     * @return the target index
     */
    public int addInterceptedMethod(TypedElement declaringType,
                                    MethodElement methodElement,
                                    String interceptedProxyClassName,
                                    String interceptedProxyBridgeMethodName) {
        hasInterceptedMethod = true;
        return addDispatchTarget(new InterceptableMethodDispatchTarget(
            findDispatchTarget(declaringType, methodElement, false),
            declaringType,
            methodElement,
            interceptedProxyClassName,
            interceptedProxyBridgeMethodName)
        );
    }

    /**
     * Adds new custom dispatch target.
     *
     * @param dispatchTarget The dispatch target implementation
     * @return the target index
     */
    public int addDispatchTarget(DispatchTarget dispatchTarget) {
        dispatchTargets.add(dispatchTarget);
        return dispatchTargets.size() - 1;
    }

    @Nullable
    public MethodDef buildDispatchMethod() {
        List<Map.Entry<DispatchTarget, Integer>> dispatchers = getDispatchers(DispatchTarget::supportsDispatchMulti);
        if (dispatchers.isEmpty()) {
            return null;
        }

        return MethodDef.override(DISPATCH_METHOD)
            .build((aThis, methodParameters) -> {

                VariableDef.MethodParameter methodIndex = methodParameters.get(0);
                VariableDef.MethodParameter target = methodParameters.get(1);
                VariableDef.MethodParameter argsArray = methodParameters.get(2);

                Map<ExpressionDef.Constant, StatementDef> switchCases = CollectionUtils.newHashMap(dispatchers.size());

                for (Map.Entry<DispatchTarget, Integer> e : dispatchers) {
                    int caseIndex = e.getValue();
                    DispatchTarget dispatchTarget = e.getKey();
                    StatementDef statementDef = dispatchTarget.dispatch(caseIndex, methodIndex, target, argsArray);
                    switchCases.put(ExpressionDef.constant(caseIndex), statementDef);
                }

                return StatementDef.multi(
                    methodParameters.get(0).asStatementSwitch(
                        TypeDef.OBJECT,
                        switchCases,
                        aThis.invoke(UNKNOWN_DISPATCH_AT_INDEX, methodIndex).doThrow()
                    ),
                    ExpressionDef.nullValue().returning()
                );
            });
    }

    private List<Map.Entry<DispatchTarget, Integer>> getDispatchers(Predicate<DispatchTarget> predicate) {
        List<Map.Entry<DispatchTarget, Integer>> result = new ArrayList<>();
        int index = 0;
        for (DispatchTarget dispatchTarget : dispatchTargets) {
            if (predicate.test(dispatchTarget)) {
                result.add(Map.entry(dispatchTarget, index));
            }
            index++;
        }
        return result;
    }

    @Nullable
    public MethodDef buildDispatchOneMethod() {
        List<Map.Entry<DispatchTarget, Integer>> dispatchers = getDispatchers(DispatchTarget::supportsDispatchOne);
        if (dispatchers.isEmpty()) {
            return null;
        }

        return MethodDef.builder("dispatchOne")
            .addModifiers(Modifier.PROTECTED, Modifier.FINAL)
            .addParameters(int.class, Object.class, Object.class)
            .returns(TypeDef.OBJECT)
            .build((aThis, methodParameters) -> {

                VariableDef.MethodParameter methodIndex = methodParameters.get(0);
                VariableDef.MethodParameter target = methodParameters.get(1);
                VariableDef.MethodParameter value = methodParameters.get(2);

                Map<ExpressionDef.Constant, StatementDef> switchCases = CollectionUtils.newHashMap(dispatchers.size());
                for (Map.Entry<DispatchTarget, Integer> e : dispatchers) {
                    int caseIndex = e.getValue();
                    DispatchTarget dispatchTarget = e.getKey();
                    StatementDef statementDef = dispatchTarget.dispatchOne(caseIndex, methodIndex, target, value);
                    switchCases.put(ExpressionDef.constant(caseIndex), statementDef);
                }

                return StatementDef.multi(
                    methodParameters.get(0).asStatementSwitch(
                        TypeDef.OBJECT,
                        switchCases,
                        aThis.invoke(UNKNOWN_DISPATCH_AT_INDEX, methodIndex).doThrow()
                    ),
                    ExpressionDef.nullValue().returning()
                );
            });
    }

    @Nullable
    public MethodDef buildGetTargetMethodByIndex() {
        // Should we include methods that don't require reflection???
        List<Map.Entry<DispatchTarget, Integer>> dispatchers = getDispatchers(dispatchTarget -> dispatchTarget.getMethodElement() != null);
        if (dispatchers.isEmpty()) {
            return null;
        }

        return MethodDef.override(GET_TARGET_METHOD)
            .build((aThis, methodParameters) -> {

                VariableDef.MethodParameter methodIndex = methodParameters.get(0);

                Map<ExpressionDef.Constant, StatementDef> switchCases = CollectionUtils.newHashMap(dispatchers.size());

                for (Map.Entry<DispatchTarget, Integer> dispatcher : dispatchers) {
                    int caseIndex = dispatcher.getValue();
                    DispatchTarget dispatchTarget = dispatcher.getKey();
                    MethodElement methodElement = dispatchTarget.getMethodElement();

                    StatementDef statement = TYPE_REFLECTION_UTILS.invokeStatic(METHOD_GET_REQUIRED_METHOD,

                        ExpressionDef.constant(ClassTypeDef.of(methodElement.getDeclaringType())),
                        ExpressionDef.constant(methodElement.getName()),
                        TypeDef.CLASS.array().instantiate(
                            Arrays.stream(methodElement.getSuspendParameters())
                                .map(p -> ExpressionDef.constant(TypeDef.erasure(p.getType())))
                                .toList()
                        )
                    ).returning();
                    switchCases.put(ExpressionDef.constant(caseIndex), statement);
                }

                return StatementDef.multi(
                    methodParameters.get(0).asStatementSwitch(
                        TypeDef.OBJECT,
                        switchCases,
                        aThis.invoke(UNKNOWN_DISPATCH_AT_INDEX, methodIndex).doThrow()
                    ),
                    ExpressionDef.nullValue().returning()
                );
            });
    }

    public static ExpressionDef getTypeUtilsGetRequiredMethod(ClassTypeDef declaringType, MethodElement methodElement) {
        List<ExpressionDef> values = new ArrayList<>();
        values.add(ExpressionDef.constant(declaringType));
        values.add(ExpressionDef.constant(methodElement.getName()));
        if (methodElement.getSuspendParameters().length > 0) {
            values.add(TypeDef.CLASS.array().instantiate(
                Arrays.stream(methodElement.getSuspendParameters())
                    .map(parameterElement -> ExpressionDef.constant(TypeDef.erasure(parameterElement.getType())))
                    .toList()
            ));
        } else {
            values.add(
                TYPE_REFLECTION_UTILS.getStaticField("EMPTY_CLASS_ARRAY", TypeDef.of(Class[].class))
            );
        }
        return TYPE_REFLECTION_UTILS.invokeStatic(METHOD_GET_REQUIRED_METHOD, values);
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        throw new IllegalStateException();
    }

    /**
     * @return all added dispatch targets
     */
    public List<DispatchTarget> getDispatchTargets() {
        return dispatchTargets;
    }

    /**
     * @return if intercepted method dispatch have been added
     */
    public boolean isHasInterceptedMethod() {
        return hasInterceptedMethod;
    }

    /**
     * Dispatch target implementation writer.
     */
    @Internal
    public interface DispatchTarget {

        /**
         * @return true if writer supports dispatch one.
         */
        boolean supportsDispatchOne();

        /**
         * @return true if writer supports dispatch multi.
         */
        boolean supportsDispatchMulti();

        default StatementDef dispatch(int caseValue, ExpressionDef caseExpression, ExpressionDef target, ExpressionDef valuesArray) {
            return dispatch(target, valuesArray);
        }

        default StatementDef dispatchOne(int caseValue, ExpressionDef caseExpression, ExpressionDef target, ExpressionDef value) {
            throw new IllegalStateException("Not supported");
        }

        StatementDef dispatch(ExpressionDef target, ExpressionDef valuesArray);

        MethodElement getMethodElement();

        TypedElement getDeclaringType();

    }

    /**
     * Dispatch target implementation writer.
     */
    @Internal
    public abstract static class AbstractDispatchTarget implements DispatchTarget {

        /**
         * Implement dispatch.
         *
         * @param target      The target
         * @param valuesArray The values array
         * @return The dispatch statement
         */
        @Override
        public StatementDef dispatch(ExpressionDef target, ExpressionDef valuesArray) {
            ExpressionDef expression = dispatchMultiExpression(target, valuesArray);
            return expressionReturning(expression);
        }

        /**
         * Implement dispatch one.
         *
         * @param caseValue      The case value
         * @param caseExpression The case expression
         * @param target         The target
         * @param value          The value
         * @return The dispatch statement
         */
        @Override
        public StatementDef dispatchOne(int caseValue, ExpressionDef caseExpression, ExpressionDef target, ExpressionDef value) {
            ExpressionDef expression = dispatchOneExpression(target, value);
            return expressionReturning(expression);
        }

        private StatementDef expressionReturning(ExpressionDef expression) {
            MethodElement methodElement = getMethodElement();
            if (methodElement != null && methodElement.getReturnType().isVoid() && !methodElement.isSuspend()) {
                return StatementDef.multi(
                    (StatementDef) expression,
                    ExpressionDef.nullValue().returning()
                );
            }
            return expression.returning();
        }

        /**
         * Implements multi dispatch.
         *
         * @param target      The target
         * @param valuesArray The values
         * @return THe expression
         */
        protected ExpressionDef dispatchMultiExpression(ExpressionDef target, ExpressionDef valuesArray) {
            MethodElement methodElement = getMethodElement();
            if (methodElement == null) {
                return dispatchMultiExpression(target, List.of(valuesArray.arrayElement(0)));
            }
            return dispatchMultiExpression(target,
                IntStream.range(0, methodElement.getSuspendParameters().length).mapToObj(valuesArray::arrayElement).toList()
            );
        }

        /**
         * Implements multi dispatch.
         *
         * @param target The target
         * @param values The values
         * @return The dispatch expression
         */
        protected ExpressionDef dispatchMultiExpression(ExpressionDef target, List<? extends ExpressionDef> values) {
            return dispatchOneExpression(target, values.get(0));
        }

        /**
         * Implements one dispatch.
         *
         * @param target The target
         * @param value  The value
         * @return The dispatch expression
         */
        protected ExpressionDef dispatchOneExpression(ExpressionDef target, ExpressionDef value) {
            return dispatchExpression(target);
        }

        /**
         * Implements dispatch.
         *
         * @param target The target
         * @return The dispatch expression
         */
        protected ExpressionDef dispatchExpression(ExpressionDef target) {
            throw new IllegalStateException("Not supported");
        }

    }

    /**
     * Field get dispatch target.
     */
    @Internal
    public static final class FieldGetDispatchTarget extends AbstractDispatchTarget {
        @NonNull
        final FieldElement beanField;

        public FieldGetDispatchTarget(FieldElement beanField) {
            this.beanField = beanField;
        }

        @Override
        public boolean supportsDispatchOne() {
            return true;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return false;
        }

        @Override
        public MethodElement getMethodElement() {
            return null;
        }

        @Override
        public TypedElement getDeclaringType() {
            return null;
        }

        @Override
        public ExpressionDef dispatchExpression(ExpressionDef bean) {
            final TypeDef propertyType = TypeDef.of(beanField.getType());
            final ClassTypeDef targetType = ClassTypeDef.of(beanField.getOwningType());

            if (beanField.isReflectionRequired()) {
                return TYPE_REFLECTION_UTILS.invokeStatic(
                    METHOD_GET_FIELD_VALUE,
                    ExpressionDef.constant(targetType), // Target class
                    ExpressionDef.constant(beanField.getName()), // Field name,
                    bean // Target instance
                ).cast(propertyType);
            } else {
                return bean.cast(targetType).field(beanField).cast(propertyType);
            }
        }

        @NonNull
        public FieldElement getField() {
            return beanField;
        }
    }

    /**
     * Field set dispatch target.
     */
    @Internal
    public static final class FieldSetDispatchTarget extends AbstractDispatchTarget {
        @NonNull
        final FieldElement beanField;

        public FieldSetDispatchTarget(FieldElement beanField) {
            this.beanField = beanField;
        }

        @Override
        public boolean supportsDispatchOne() {
            return true;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return false;
        }

        @Override
        public MethodElement getMethodElement() {
            return null;
        }

        @Override
        public TypedElement getDeclaringType() {
            return null;
        }

        @Override
        public StatementDef dispatchOne(int caseValue, ExpressionDef caseExpression, ExpressionDef target, ExpressionDef value) {
            final TypeDef propertyType = TypeDef.of(beanField.getType());
            final ClassTypeDef targetType = ClassTypeDef.of(beanField.getOwningType());
            if (beanField.isReflectionRequired()) {
                return TYPE_REFLECTION_UTILS.invokeStatic(METHOD_SET_FIELD_VALUE,
                    ExpressionDef.constant(targetType), // Target class
                    ExpressionDef.constant(beanField.getName()), // Field name
                    target, // Target instance
                    value // Field value
                ).after(ExpressionDef.nullValue().returning());
            } else {
                return target.cast(targetType)
                    .field(beanField)
                    .put(value.cast(propertyType))
                    .after(ExpressionDef.nullValue().returning());
            }
        }

        @NonNull
        public FieldElement getField() {
            return beanField;
        }
    }

    /**
     * Method invocation dispatch target.
     */
    @Internal
    public static final class MethodDispatchTarget extends AbstractDispatchTarget {
        final ClassElement declaringType;
        final MethodElement methodElement;
        private final boolean useOneDispatch;

        private MethodDispatchTarget(ClassElement targetType,
                                     MethodElement methodElement,
                                     boolean useOneDispatch) {
            this.declaringType = targetType;
            this.methodElement = methodElement;
            this.useOneDispatch = useOneDispatch;
        }

        @Override
        public boolean supportsDispatchOne() {
            return useOneDispatch;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return !useOneDispatch;
        }

        @Override
        public ClassElement getDeclaringType() {
            return declaringType;
        }

        @Override
        public MethodElement getMethodElement() {
            return methodElement;
        }

        @Override
        public ExpressionDef dispatchMultiExpression(ExpressionDef target, List<? extends ExpressionDef> values) {
            ClassTypeDef targetType = ClassTypeDef.of(declaringType);
            if (methodElement.isStatic()) {
                return targetType.invokeStatic(methodElement, values);
            }
            return target.cast(targetType).invoke(methodElement, values);
        }

        @Override
        public ExpressionDef dispatchOneExpression(ExpressionDef target, ExpressionDef value) {
            ClassTypeDef targetType = ClassTypeDef.of(declaringType);
            if (methodElement.isStatic()) {
                return targetType.invokeStatic(methodElement, TypeDef.OBJECT.array().instantiate(value));
            }
            if (methodElement.getSuspendParameters().length > 0) {
                return target.cast(targetType).invoke(methodElement, value);
            }
            return target.cast(targetType).invoke(methodElement);
        }
    }

    /**
     * Method invocation dispatch target.
     */
    @Internal
    public static final class KotlinMethodWithDefaultsDispatchTarget extends AbstractDispatchTarget {
        final ClassElement declaringType;
        final MethodElement methodElement;
        private final boolean useOneDispatch;

        private KotlinMethodWithDefaultsDispatchTarget(ClassElement targetType,
                                                       MethodElement methodElement,
                                                       boolean useOneDispatch) {
            this.declaringType = targetType;
            this.methodElement = methodElement;
            this.useOneDispatch = useOneDispatch;
        }

        @Override
        public boolean supportsDispatchOne() {
            return useOneDispatch;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return !useOneDispatch;
        }

        @Override
        public ClassElement getDeclaringType() {
            return declaringType;
        }

        @Override
        public MethodElement getMethodElement() {
            return methodElement;
        }

        @Override
        public ExpressionDef dispatchMultiExpression(ExpressionDef target, List<? extends ExpressionDef> values) {
            return MethodGenUtils.invokeKotlinDefaultMethod(declaringType, methodElement, target, values);
        }

        @Override
        public ExpressionDef dispatchOneExpression(ExpressionDef target, ExpressionDef value) {
            return MethodGenUtils.invokeKotlinDefaultMethod(declaringType, methodElement, target, List.of(value));
        }
    }

    /**
     * Method invocation dispatch target.
     */
    @Internal
    public static final class MethodReflectionDispatchTarget extends AbstractDispatchTarget {
        private final TypedElement declaringType;
        private final MethodElement methodElement;
        private final int methodIndex;
        private final boolean useOneDispatch;

        private MethodReflectionDispatchTarget(TypedElement declaringType,
                                               MethodElement methodElement,
                                               int methodIndex,
                                               boolean useOneDispatch) {
            this.declaringType = declaringType;
            this.methodElement = methodElement;
            this.methodIndex = methodIndex;
            this.useOneDispatch = useOneDispatch;
        }

        @Override
        public boolean supportsDispatchOne() {
            return useOneDispatch;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return !useOneDispatch;
        }

        @Override
        public TypedElement getDeclaringType() {
            return declaringType;
        }

        @Override
        public MethodElement getMethodElement() {
            return methodElement;
        }

        @Override
        public ExpressionDef dispatchMultiExpression(ExpressionDef target, ExpressionDef valuesArray) {
            return TYPE_REFLECTION_UTILS.invokeStatic(
                METHOD_INVOKE_METHOD,

                methodElement.isStatic() ? ExpressionDef.nullValue() : target,
                new VariableDef.This().invoke(GET_ACCESSIBLE_TARGET_METHOD, ExpressionDef.constant(methodIndex)),
                valuesArray
            );
        }

        @Override
        public ExpressionDef dispatchOneExpression(ExpressionDef target, ExpressionDef value) {
            return TYPE_REFLECTION_UTILS.invokeStatic(
                METHOD_INVOKE_METHOD,

                methodElement.isStatic() ? ExpressionDef.nullValue() : target,
                new VariableDef.This().invoke(GET_ACCESSIBLE_TARGET_METHOD, ExpressionDef.constant(methodIndex)),
                methodElement.getSuspendParameters().length > 0 ? TypeDef.OBJECT.array().instantiate(value) : TypeDef.OBJECT.array().instantiate()
            );
        }

    }

    /**
     * Interceptable method invocation dispatch target.
     */
    @Internal
    public static final class InterceptableMethodDispatchTarget extends AbstractDispatchTarget {
        private final TypedElement declaringType;
        private final DispatchTarget dispatchTarget;
        private final String interceptedProxyClassName;
        private final String interceptedProxyBridgeMethodName;
        private final MethodElement methodElement;

        private InterceptableMethodDispatchTarget(DispatchTarget dispatchTarget,
                                                  TypedElement declaringType,
                                                  MethodElement methodElement,
                                                  String interceptedProxyClassName,
                                                  String interceptedProxyBridgeMethodName) {
            this.declaringType = declaringType;
            this.methodElement = methodElement;
            this.dispatchTarget = dispatchTarget;
            this.interceptedProxyClassName = interceptedProxyClassName;
            this.interceptedProxyBridgeMethodName = interceptedProxyBridgeMethodName;
        }

        @Override
        public boolean supportsDispatchOne() {
            return false;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return true;
        }

        @Override
        public TypedElement getDeclaringType() {
            return declaringType;
        }

        @Override
        public MethodElement getMethodElement() {
            return methodElement;
        }

        @Override
        public StatementDef dispatch(ExpressionDef target, ExpressionDef valuesArray) {
            VariableDef.Field interceptableField = new VariableDef.This()
                .field(FIELD_INTERCEPTABLE, TypeDef.of(boolean.class));

            ClassTypeDef proxyType = ClassTypeDef.of(interceptedProxyClassName);

            return interceptableField.isTrue().and(target.instanceOf(proxyType))
                .doIfElse(
                    invokeProxyBridge(proxyType, target, valuesArray),
                    dispatchTarget.dispatch(target, valuesArray)
                );
        }

        private StatementDef invokeProxyBridge(ClassTypeDef proxyType, ExpressionDef target, ExpressionDef valuesArray) {
            boolean suspend = methodElement.isSuspend();
            ExpressionDef.InvokeInstanceMethod invoke = target.cast(proxyType).invoke(
                interceptedProxyBridgeMethodName,
                Arrays.stream(methodElement.getSuspendParameters()).map(p -> TypeDef.of(p.getType())).toList(),
                suspend ? TypeDef.OBJECT : TypeDef.of(methodElement.getReturnType()),
                IntStream.range(0, methodElement.getSuspendParameters().length).mapToObj(valuesArray::arrayElement).toList()
            );
            if (dispatchTarget.getMethodElement().getReturnType().isVoid() && !suspend) {
                return StatementDef.multi(
                    invoke,
                    ExpressionDef.nullValue().returning()
                );
            }
            return invoke.returning();
        }
    }

}
