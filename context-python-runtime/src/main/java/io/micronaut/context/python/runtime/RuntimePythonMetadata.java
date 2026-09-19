/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python.runtime;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.sourcegen.bytecode.ByteCodeWriter;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime bytecode backend. Its cache follows wrapper class identity, including classloader identity.
 * No javac, annotation processor, or captured compile-time AST is used here.
 */
@Internal
public final class RuntimePythonMetadata {
    private static final ClassValue<State> STATES = new ClassValue<>() {
        @Override
        protected State computeValue(Class<?> type) {
            // ClassValue may compute competing values. Do not define classes until the winning state is used.
            return new State(type);
        }
    };

    private RuntimePythonMetadata() {
    }

    static BeanDefinition<?> definition(Class<?> beanType) {
        return STATES.get(beanType).definition();
    }

    static BeanIntrospection<?> introspection(Class<?> beanType) {
        return STATES.get(beanType).introspection();
    }

    /**
     * Reports whether this wrapper's definition has actually been generated.
     *
     * @param beanType The Python wrapper class
     * @return Whether this wrapper's definition has actually been generated
     */
    public static boolean isDefinitionGenerated(Class<?> beanType) {
        return STATES.get(beanType).definition != null;
    }

    /**
     * Reports whether this wrapper's introspection has actually been generated.
     *
     * @param beanType The Python wrapper class
     * @return Whether this wrapper's introspection has actually been generated
     */
    public static boolean isIntrospectionGenerated(Class<?> beanType) {
        return STATES.get(beanType).introspection != null;
    }

    private static ClassDef.ClassDefBuilder builder(Class<?> beanType, String suffix, Class<?> superType) {
        return ClassDef.builder(beanType.getPackageName() + ".$" + beanType.getSimpleName() + suffix)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(superType)
            .addMethod(MethodDef.constructor().addModifiers(Modifier.PUBLIC)
                .addParameter("beanType", Class.class)
                .build((self, params) -> self.superRef().invokeSuperConstructor(params.getFirst())));
    }

    private static ClassDef definitionModel(Class<?> beanType) {
        return builder(beanType, "$RuntimeDefinition", RuntimePythonBeanDefinition.class)
            .addMethod(MethodDef.override(ReflectionUtils.getRequiredMethod(InstantiatableBeanDefinition.class,
                    "instantiate", BeanResolutionContext.class, BeanContext.class))
                .build((self, params) -> ClassTypeDef.of(beanType).instantiate().returning()))
            .build();
    }

    private static ClassDef introspectionModel(Class<?> beanType, RuntimePythonModel model) {
        return builder(beanType, "$RuntimeIntrospection", RuntimePythonIntrospection.class)
            .addMethod(MethodDef.override(ReflectionUtils.getRequiredMethod(RuntimePythonIntrospection.class, "instantiate"))
                .build((self, params) -> ClassTypeDef.of(beanType).instantiate().returning()))
            .addMethod(MethodDef.builder("dispatchOne").addModifiers(Modifier.PROTECTED)
                .returns(Object.class).addParameter("index", int.class)
                .addParameter("target", Object.class).addParameter("value", Object.class)
                .build((self, params) -> {
                    Map<ExpressionDef.Constant, StatementDef> cases = new LinkedHashMap<>();
                    var target = params.get(1).cast(beanType);
                    for (int i = 0; i < model.properties().size(); i++) {
                        var property = model.properties().get(i);
                        // These are resolved wrapper methods, not runtime reflective property access.
                        var getter = ReflectionUtils.getRequiredMethod(beanType, property.read());
                        var setter = ReflectionUtils.getRequiredMethod(beanType, property.write(), property.javaType());
                        cases.put(ExpressionDef.constant(i * 2), target.invoke(getter).cast(Object.class).returning());
                        cases.put(ExpressionDef.constant(i * 2 + 1), StatementDef.multi(
                            target.invoke(setter, params.get(2).cast(property.javaType())),
                            ExpressionDef.nullValue().returning()));
                    }
                    return params.getFirst().asStatementSwitch(TypeDef.OBJECT, cases,
                        ClassTypeDef.of(IllegalArgumentException.class)
                            .instantiate(ExpressionDef.constant("Unknown runtime property index")).doThrow());
                }))
            .build();
    }

    private static Object generate(Class<?> beanType, ClassDef model) {
        try {
            byte[] bytes = new ByteCodeWriter().write(model);
            // Same loader and runtime package as the wrapper; no child-loader access surprises.
            var lookup = MethodHandles.privateLookupIn(beanType, MethodHandles.lookup());
            Class<?> generated = lookup.defineClass(bytes);
            return lookup.findConstructor(generated, MethodType.methodType(void.class, Class.class)).invoke(beanType);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot generate runtime metadata for " + beanType.getName(), e);
        }
    }

    private static final class State {
        private final Class<?> beanType;
        private volatile @Nullable BeanDefinition<?> definition;
        private volatile @Nullable BeanIntrospection<?> introspection;

        private State(Class<?> beanType) {
            this.beanType = beanType;
        }

        synchronized BeanDefinition<?> definition() {
            if (definition == null) {
                if (!RuntimePythonModel.read(beanType).singleton()) {
                    throw new IllegalStateException("The runtime model is not a singleton: " + beanType.getName());
                }
                definition = (BeanDefinition<?>) generate(beanType, definitionModel(beanType));
            }
            return definition;
        }

        synchronized BeanIntrospection<?> introspection() {
            if (introspection == null) {
                introspection = (BeanIntrospection<?>) generate(beanType, introspectionModel(beanType, RuntimePythonModel.read(beanType)));
            }
            return introspection;
        }
    }
}
