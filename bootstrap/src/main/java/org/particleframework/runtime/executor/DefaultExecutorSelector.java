/*
 * Copyright 2017 original authors
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
package org.particleframework.runtime.executor;

import org.particleframework.context.BeanLocator;
import org.particleframework.core.annotation.Blocking;
import org.particleframework.inject.MethodReference;
import org.particleframework.inject.qualifiers.Qualifiers;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Default implementation of the {@link ExecutorSelector} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultExecutorSelector implements ExecutorSelector {

    protected static final String[] DEFAULT_BLOCKING_ANNOTATIONS = {
            Blocking.class.getName(),
            "grails.gorm.transactions.Transactional",
            "grails.gorm.transactions.ReadOnly",
            "org.springframework.transaction.annotation.Transactional",
    };
    private final BeanLocator beanLocator;
    private final String[] blockingAnnotations;


    public DefaultExecutorSelector(BeanLocator beanLocator) {
        this(beanLocator, DEFAULT_BLOCKING_ANNOTATIONS);
    }

    protected DefaultExecutorSelector(BeanLocator beanLocator, String... blockingAnnotations) {
        this.beanLocator = beanLocator;
        this.blockingAnnotations = blockingAnnotations;
    }

    @Override
    public Optional<ExecutorService> select(MethodReference method) {
        if( method.isAnyAnnotationPresent(blockingAnnotations) ) {
            return beanLocator.findBean(
                    ExecutorService.class,
                    Qualifiers.byName(IOExecutorService.NAME)
            );
        }
        return Optional.empty();
    }
}
