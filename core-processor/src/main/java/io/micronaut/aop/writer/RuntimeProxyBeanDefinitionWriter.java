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
package io.micronaut.aop.writer;

import io.micronaut.aop.runtime.DefaultRuntimeProxyDefinition;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.aop.runtime.RuntimeProxyCreator;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.lang.reflect.Method;

/**
 * The writer for runtime proxy bean definitions.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public class RuntimeProxyBeanDefinitionWriter extends ProxyingBeanDefinitionWriter {

    private static final Method GET_BEAN = ReflectionUtils.getRequiredInternalMethod(
        BeanResolutionContext.class,
        "getBean",
        Class.class);

    private static final Method CREATE_PROXY = ReflectionUtils.getRequiredInternalMethod(
        RuntimeProxyCreator.class,
        "createProxy",
        RuntimeProxyDefinition.class);

    private static final Method AROUND = ReflectionUtils.getRequiredInternalMethod(
        DefaultRuntimeProxyDefinition.class,
        "around",
        BeanResolutionContext.class, BeanDefinition.class, boolean.class, Object[].class);

    private static final Method INTRODUCTION = ReflectionUtils.getRequiredInternalMethod(
        DefaultRuntimeProxyDefinition.class,
        "introduction",
        BeanResolutionContext.class, BeanDefinition.class);

    public static final String RUNTIME_PROXY_SUFFIX = "$RuntimeProxy";

    public RuntimeProxyBeanDefinitionWriter(ClassElement targetType, BeanDefinitionWriter parent, OptionalValues<Boolean> settings, VisitorContext visitorContext, AnnotationValue<?>... interceptorBinding) {
        super(RUNTIME_PROXY_SUFFIX, targetType, targetType, parent, settings, visitorContext, interceptorBinding);
    }

    public RuntimeProxyBeanDefinitionWriter(ClassElement targetType, ClassElement[] interfaceTypes, VisitorContext visitorContext, AnnotationValue<?>... interceptorBinding) {
        super(RUNTIME_PROXY_SUFFIX, targetType, targetType, interfaceTypes, visitorContext, interceptorBinding);
    }

    public RuntimeProxyBeanDefinitionWriter(String suffix, ClassElement targetType, boolean implementInterface, ClassElement[] interfaceTypes, VisitorContext visitorContext, AnnotationValue<?>... interceptorBinding) {
        super(suffix + RUNTIME_PROXY_SUFFIX, targetType, targetType, implementInterface, interfaceTypes, visitorContext, interceptorBinding);
    }

    @Override
    protected boolean getProxyTarget(ClassElement targetType, BeanDefinitionWriter parent, OptionalValues<Boolean> settings) {
        return super.getProxyTarget(targetType, parent, settings) || targetType.isTrue(RuntimeProxy.class, "proxyTarget");
    }

    @Override
    protected BeanDefinitionWriter createAdviceProxyBeanDefinitionWriter(String suffix) {
        return new BeanDefinitionWriter(
            ClassElement.of(parentWriter.getPackageName() + '.' + parentWriter.getBeanSimpleName(), parentWriter.isInterface(), parentWriter.getAnnotationMetadata()),
            targetType.getName() + suffix,
            parentWriter,
            visitorContext,
            null
        );
    }

    @Override
    protected BeanDefinitionWriter createIntroductionProxyBeanDefinitionWriter(String suffix) {
        return new BeanDefinitionWriter(
            ClassElement.of(
                targetType.getName(),
                targetType.isInterface(),
                targetType.getAnnotationMetadata()
            ),
            targetType.getName() + suffix,
            this,
            visitorContext,
            null
        );
    }

    @Override
    public void postConstructor() {
        String runtimeProxyClass = targetType.stringValue(RuntimeProxy.class)
            .orElseThrow(() -> new ProcessingException(targetType, "Missing runtime proxy creator"));
        proxyBeanDefinitionWriter.visitBuildCustomMethodDefinition((statements, aThis, methodParameters, constructorValues) -> {
            TypeDef runtimeProxyCreatorClass = TypeDef.of(runtimeProxyClass);
            ExpressionDef runtimeProxyDefinition;
            if (isIntroduction) {
                runtimeProxyDefinition = ClassTypeDef.of(DefaultRuntimeProxyDefinition.class)
                    .invokeStatic(INTRODUCTION, methodParameters.getFirst(), aThis);
            } else {
                runtimeProxyDefinition = ClassTypeDef.of(DefaultRuntimeProxyDefinition.class)
                    .invokeStatic(AROUND, methodParameters.getFirst(), aThis, ExpressionDef.constant(isProxyTarget), TypeDef.OBJECT.array().instantiate(constructorValues));
            }
            return methodParameters.getFirst()
                .invoke(GET_BEAN, ExpressionDef.constant(runtimeProxyCreatorClass))
                .cast(runtimeProxyCreatorClass)
                .invoke(CREATE_PROXY, runtimeProxyDefinition);
        });
    }
}
