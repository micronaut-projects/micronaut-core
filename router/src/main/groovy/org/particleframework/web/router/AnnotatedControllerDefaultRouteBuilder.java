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
package org.particleframework.web.router;

import org.particleframework.context.ApplicationContext;
import org.particleframework.context.processor.ExecutableMethodProcessor;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.naming.conventions.MethodConvention;
import org.particleframework.core.naming.conventions.PropertyConvention;
import org.particleframework.http.HttpMethod;
import org.particleframework.inject.Argument;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.stereotype.Controller;

import java.util.Arrays;
import java.util.Optional;

/**
 * <p>This {@link RouteBuilder} will handle public methods of {@link Controller} instances that are mapped by convention</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotatedControllerDefaultRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<Controller> {

    public AnnotatedControllerDefaultRouteBuilder(ApplicationContext beanContext, UriNamingStrategy uriNamingStrategy) {
        super(beanContext, uriNamingStrategy);
    }

    @Override
    public void process(ExecutableMethod method) {
        Class<?> declaringType = method.getDeclaringType();
        Controller controllerAnn = AnnotationUtil.findAnnotationWithStereoType(declaringType, Controller.class);
        if(controllerAnn != null ) {
            if(!controllerAnn.value().isEmpty()) {

                String methodName = method.getMethodName();
                Optional<MethodConvention> methodConvention = MethodConvention.forMethod(methodName);


                Optional<Argument> idArg = Arrays.stream(method.getArguments())
                        .filter((argument -> argument.getName()
                                .equals(PropertyConvention.ID.lowerCaseName())))
                        .findFirst();

                String id = idArg.map((arg)-> "{/id}").orElse("");
                methodConvention.ifPresent((convention)->
                        buildRoute( HttpMethod.valueOf(convention.httpMethod()),
                                controllerAnn.value() + id,
                                declaringType,
                                methodName,
                                method.getArgumentTypes()
                        ));
            }
            else {
                Class[] argumentTypes = method.getArgumentTypes();
                if(argumentTypes.length > 0 && Throwable.class.isAssignableFrom(argumentTypes[0])) {
                    Class argumentType = argumentTypes[0];
                    error(method.getDeclaringType(), argumentType, declaringType, method.getMethodName(), method.getArgumentTypes());
                }
            }
        }
    }
}
