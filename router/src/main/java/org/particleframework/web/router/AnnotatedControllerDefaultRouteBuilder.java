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

import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.context.processor.ExecutableMethodProcessor;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.naming.conventions.MethodConvention;
import org.particleframework.core.naming.conventions.PropertyConvention;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.MediaType;
import org.particleframework.inject.Argument;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.stereotype.Controller;
import org.particleframework.web.router.annotation.Action;
import org.particleframework.web.router.annotation.Consumes;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Optional;

/**
 * <p>This {@link RouteBuilder} will handle public methods of {@link Controller} instances that are mapped by convention</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class AnnotatedControllerDefaultRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<Controller> {

    public AnnotatedControllerDefaultRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
    }

    @Override
    public void process(ExecutableMethod method) {
        Class<?> declaringType = method.getDeclaringType();
        Controller controllerAnn = AnnotationUtil.findAnnotationWithStereoType(declaringType, Controller.class);
        if (controllerAnn != null && AnnotationUtil.findAnnotationWithStereoType(method, Action.class) == null) {


            Class[] argumentTypes = method.getArgumentTypes();
            if (argumentTypes.length > 0 && Throwable.class.isAssignableFrom(argumentTypes[0])) {
                Class argumentType = argumentTypes[0];
                ErrorRoute errorRoute = error(method.getDeclaringType(), argumentType, declaringType, method.getMethodName(), method.getArgumentTypes());
                errorRoute = (ErrorRoute) processAccepts(controllerAnn, errorRoute);
                processAccepts(declaringType.getAnnotation(Consumes.class), errorRoute);
            }
            else {
                String annotationValue = controllerAnn.value();
                String path;
                if(annotationValue.isEmpty()) {
                    path = getUriNamingStrategy().resolveUri(declaringType);
                }
                else {
                    if(!annotationValue.startsWith("/")) {
                        annotationValue = '/'+annotationValue;
                    }

                    path = annotationValue;
                }
                String methodName = method.getMethodName();
                Optional<MethodConvention> methodConvention = MethodConvention.forMethod(methodName);


                Optional<Argument> idArg = Arrays.stream(method.getArguments())
                        .filter((argument -> argument.getName()
                                .equals(PropertyConvention.ID.lowerCaseName())))
                        .findFirst();

                String id = idArg.map((arg) -> "{/id}").orElse("");
                methodConvention.ifPresent((convention) -> {
                    UriRoute uriRoute = buildRoute(HttpMethod.valueOf(convention.httpMethod()),
                            path + id,
                            declaringType,
                            methodName,
                            method.getArgumentTypes()
                    );
                    uriRoute = (UriRoute) processAccepts(controllerAnn, uriRoute);
                    processAccepts(declaringType.getAnnotation(Consumes.class), uriRoute);
                });

            }

        }
    }

    protected Route processAccepts(Controller controllerAnn, Route route) {
        String[] consumes = controllerAnn.consumes();
        return processConsumes(route, consumes);
    }

    protected Route processAccepts(Consumes consumesAnn, Route route) {
        if(consumesAnn != null) {
            String[] consumes = consumesAnn.value();
            return processConsumes(route, consumes);
        }
        return route;
    }


    private Route processConsumes(Route route, String... consumes) {
        MediaType[] accepts = Arrays.stream(consumes).map(MediaType::new).toArray(MediaType[]::new);
        return route.accept(accepts);
    }
}
