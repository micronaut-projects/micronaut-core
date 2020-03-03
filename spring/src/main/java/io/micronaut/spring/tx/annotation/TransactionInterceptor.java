/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.spring.tx.annotation;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionAttribute;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple interceptor to for {@link Transactional}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class TransactionInterceptor extends TransactionAspectSupport implements MethodInterceptor<Object, Object> {

    private final Map<ExecutableMethod, TransactionAttribute> transactionDefinitionMap = new ConcurrentHashMap<>();
    private final Map<String, PlatformTransactionManager> transactionManagerMap = new ConcurrentHashMap<>();
    private final BeanLocator beanLocator;

    /**
     * @param beanLocator The {@link BeanLocator}
     */
    public TransactionInterceptor(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public final Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.hasStereotype(Transactional.class)) {
            String transactionManagerName = context.findAnnotation(Transactional.class).flatMap(AnnotationValue::stringValue).orElse(null);
            if (StringUtils.isEmpty(transactionManagerName)) {
                transactionManagerName = null;
            }
            PlatformTransactionManager transactionManager = resolveTransactionManager(transactionManagerName);

            String finalTransactionManagerName = transactionManagerName;
            TransactionAttribute transactionDefinition = resolveTransactionAttribute(
                context.getExecutableMethod(),
                context,
                finalTransactionManagerName
            );

            final TransactionInfo transactionInfo = createTransactionIfNecessary(
                    transactionManager,
                    transactionDefinition,
                    context.getDeclaringType().getName() + "." + context.getMethodName());
            Object retVal;
            try {
                retVal = context.proceed();
            } catch (Throwable ex) {
                completeTransactionAfterThrowing(transactionInfo, ex);
                throw ex;
            } finally {
                cleanupTransactionInfo(transactionInfo);
            }
            commitTransactionAfterReturning(transactionInfo);
            return retVal;
        } else {
            return context.proceed();
        }
    }

    @Override
    public void afterPropertiesSet() {
        // no-op - override this because TransactionAspectSupport provides some undesired validation behaviour
    }

    /**
     * @param targetMethod           The target method
     * @param annotationMetadata     The annotation metadata
     * @param transactionManagerName The transaction manager
     * @return The {@link TransactionAttribute}
     */
    protected TransactionAttribute resolveTransactionAttribute(
            ExecutableMethod<Object, Object> targetMethod,
            AnnotationMetadata annotationMetadata,
            String transactionManagerName) {
        return transactionDefinitionMap.computeIfAbsent(targetMethod, method -> {
            AnnotationValue<Transactional> annotation = annotationMetadata.getAnnotation(Transactional.class);

            if (annotation == null) {
                throw new IllegalStateException("No declared @Transactional annotation present");
            }

            BindableRuleBasedTransactionAttribute attribute = new BindableRuleBasedTransactionAttribute();
            attribute.setReadOnly(annotation.getRequiredValue("readOnly", Boolean.class));
            attribute.setTimeout(annotation.getRequiredValue("timeout", Integer.class));
            //noinspection unchecked
            attribute.setRollbackFor(annotation.get("rollbackFor", Class[].class).orElse(ReflectionUtils.EMPTY_CLASS_ARRAY));
            //noinspection unchecked
            attribute.setNoRollbackFor(annotation.get("noRollbackFor", Class[].class).orElse(ReflectionUtils.EMPTY_CLASS_ARRAY));
            attribute.setPropagationBehavior(annotation.getRequiredValue("propagation", Propagation.class).value());
            attribute.setIsolationLevel(annotation.getRequiredValue("isolation", Isolation.class).value());
            attribute.setQualifier(transactionManagerName);
            return attribute;
        });
    }

    private PlatformTransactionManager resolveTransactionManager(String transactionManagerName) {
        try {
            if (transactionManagerName != null) {
                return this.transactionManagerMap.computeIfAbsent(transactionManagerName, s ->
                    beanLocator.getBean(PlatformTransactionManager.class, Qualifiers.byName(transactionManagerName))
                );
            } else {
                return this.transactionManagerMap.computeIfAbsent("default", s ->
                    beanLocator.getBean(PlatformTransactionManager.class)
                );
            }
        } catch (NoSuchBeanException e) {
            throw new TransactionSystemException("No transaction manager configured" + (transactionManagerName != null ? " for name: " + transactionManagerName : ""));
        }
    }
}
