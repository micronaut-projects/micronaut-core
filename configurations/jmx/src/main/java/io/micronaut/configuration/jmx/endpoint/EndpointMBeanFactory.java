/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.configuration.jmx.endpoint;

import io.micronaut.configuration.jmx.context.AbstractDynamicMBeanFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.annotation.Delete;
import io.micronaut.management.endpoint.annotation.Write;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.management.MBeanOperationInfo;

/**
 * A dynamic mbean factory for endpoints.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Named("endpoint")
public class EndpointMBeanFactory extends AbstractDynamicMBeanFactory {

    @Override
    protected String getBeanDescription(BeanDefinition beanDefinition) {
        return "";
    }

    @Override
    protected String getMethodDescription(ExecutableMethod method) {
        return "";
    }

    @Override
    protected String getParameterDescription(Argument argument) {
        return "";
    }

    @Override
    protected int getImpact(ExecutableMethod method) {
        int impact = MBeanOperationInfo.INFO; //read
        if (method.hasAnnotation(Write.class)) {
            if (method.getReturnType().getType() == void.class) {
                impact = MBeanOperationInfo.ACTION;
            } else {
                impact = MBeanOperationInfo.ACTION_INFO;
            }
        } else if (method.hasAnnotation(Delete.class)) {
            impact = MBeanOperationInfo.ACTION;
        }
        return impact;
    }
}
