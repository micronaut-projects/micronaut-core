/*
 * Copyright 2018 original authors
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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple interceptor to for {@link Transactional}
 *
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class TransactionInterceptor implements MethodInterceptor<Object,Object> {

    private final Map<Method, TransactionAttribute> transactionDefinitionMap = new ConcurrentHashMap<>();
    private final Map<String, PlatformTransactionManager> transactionManagerMap = new ConcurrentHashMap<>();
    private final BeanLocator beanLocator;

    public TransactionInterceptor(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public final Object intercept(MethodInvocationContext<Object, Object> context) {

        if(context.hasDeclaredAnnotation(Transactional.class)) {
            String transactionManagerName = context.getValue(Transactional.class, String.class).orElse(null);
            if(StringUtils.isEmpty(transactionManagerName)) {
                transactionManagerName = null;
            }
            PlatformTransactionManager transactionManager = resolveTransactionManager(transactionManagerName);

            String finalTransactionManagerName = transactionManagerName;
            TransactionAttribute transactionDefinition = transactionDefinitionMap.computeIfAbsent(context.getTargetMethod(), method -> {
                Map<String, Object> attributeMap = context.getValues(Transactional.class).asMap();
                DefaultTransactionAttribute defaultTransactionAttribute = ConversionService.SHARED.convert(attributeMap, DefaultTransactionAttribute.class)
                        .orElseThrow(() -> new TransactionSystemException("Unable to create TransactionAttribute for map: " + attributeMap));
                if(finalTransactionManagerName != null) {
                    defaultTransactionAttribute.setQualifier(finalTransactionManagerName);
                }
                return defaultTransactionAttribute;
            });


            TransactionTemplate template = new TransactionTemplate(
                transactionManager,
                transactionDefinition
            );

            return template.execute(status -> context.proceed());
        }
        else {
            return context.proceed();
        }
    }

    private PlatformTransactionManager resolveTransactionManager(String transactionManagerName) {
        try {
            if(transactionManagerName != null) {
                return this.transactionManagerMap.computeIfAbsent(transactionManagerName, s ->
                        beanLocator.getBean(PlatformTransactionManager.class, Qualifiers.byName(transactionManagerName))
                );
            }
            else {
                return this.transactionManagerMap.computeIfAbsent("default", s ->
                        beanLocator.getBean(PlatformTransactionManager.class)
                );
            }
        } catch (NoSuchBeanException e) {
            throw new TransactionSystemException("No transaction manager configured" + (transactionManagerName != null ? " for name: " + transactionManagerName : ""));
        }
    }
}
