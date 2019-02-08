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
package io.micronaut.configuration.jmx.context;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.management.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A base class that creates dynamic MBeans from a bean definition.
 *
 * @author James Kleeh
 * @since 1.0
 */
public abstract class AbstractDynamicMBeanFactory implements DynamicMBeanFactory {

    @Override
    public Object createMBean(BeanDefinition beanDefinition, Collection<ExecutableMethod> methods, Supplier<Object> instanceSupplier) {

        MBeanOperationInfo[] operations = methods.stream().map(this::getOperationInfo).toArray(MBeanOperationInfo[]::new);
        MBeanInfo mBeanInfo = new MBeanInfo(beanDefinition.getBeanType().getName(),
                getBeanDescription(beanDefinition),
                new MBeanAttributeInfo[0],
                new MBeanConstructorInfo[0],
                operations,
                new MBeanNotificationInfo[0]
        );

        return new DynamicMBean() {

            @Override
            public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
                return null;
            }

            @Override
            public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
                //no op
            }

            @Override
            public AttributeList getAttributes(String[] attributes) {
                return new AttributeList();
            }

            @Override
            public AttributeList setAttributes(AttributeList attributes) {
                return attributes;
            }

            @Override
            public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
                List<ExecutableMethod> matchedMethods = methods.stream()
                        .filter(m -> m.getMethodName().equals(actionName))
                        .collect(Collectors.toList());
                if (matchedMethods.size() == 1) {
                    //noinspection unchecked
                    Object returnVal = matchedMethods.get(0).invoke(instanceSupplier.get(), params);
                    if (returnVal != null) {
                        if (Publishers.isSingle(returnVal.getClass()) || Publishers.isConvertibleToPublisher(returnVal)) {
                            return Flowable.fromPublisher(Publishers.convertPublisher(returnVal, Publisher.class)).blockingFirst();
                        }
                    }

                    return returnVal;
                } else {
                    //would be necessary at this point to convert the signature string[] to a class[]
                    //in order to find the correct method
                    return null;
                }
            }

            @Override
            public MBeanInfo getMBeanInfo() {
                return mBeanInfo;
            }
        };
    }

    /**
     * Returns the management bean description.
     * @see MBeanInfo#getDescription()
     *
     * @param beanDefinition The bean definition
     * @return The description for the management bean
     */
    protected abstract String getBeanDescription(BeanDefinition beanDefinition);

    /**
     * Returns the description of a management bean operation.
     * @see MBeanOperationInfo#getDescription()
     *
     * @param method The method
     * @return The description for the management bean operation
     */
    protected abstract String getMethodDescription(ExecutableMethod method);

    /**
     * Returns the description of a management bean operation parameter.
     * @see MBeanParameterInfo#getDescription()
     *
     * @param argument The argument
     * @return The description for the management bean operation parameter
     */
    protected abstract String getParameterDescription(Argument argument);

    /**
     * Extracts parameters from an executable method.
     * @see MBeanOperationInfo#getSignature()
     *
     * @param method The method
     * @return The array of management bean operation parameters
     */
    protected MBeanParameterInfo[] getParameters(ExecutableMethod method) {
        return Arrays.stream(method.getArguments()).map(argument -> {
            return new MBeanParameterInfo(argument.getName(), argument.getType().getName(), getParameterDescription(argument));
        }).toArray(MBeanParameterInfo[]::new);
    }

    /**
     * Returns the return type of the executable method.
     * @see MBeanOperationInfo#getReturnType()
     *
     * @param method The method
     * @return The return type of the method
     */
    protected String getReturnType(ExecutableMethod method) {
        Class returnType = method.getReturnType().getType();
        if (Publishers.isSingle(returnType) || Publishers.isConvertibleToPublisher(returnType)) {
            Argument[] typeParams = method.getReturnType().getTypeParameters();
            if (typeParams.length > 0) {
                returnType = typeParams[0].getType();
            }
        }
        return returnType.getName();
    }

    /**
     * Returns the impact of the provided method.
     * @see MBeanOperationInfo#getImpact()
     *
     * @param method The method
     * @return The impact
     */
    protected abstract int getImpact(ExecutableMethod method);

    /**
     * Returns the operation information.
     * @see MBeanInfo#getOperations()
     *
     * @param method The method
     * @return The operation information
     */
    protected MBeanOperationInfo getOperationInfo(ExecutableMethod method) {
        return new MBeanOperationInfo(method.getMethodName(), getMethodDescription(method), getParameters(method), getReturnType(method), getImpact(method));
    }

}
