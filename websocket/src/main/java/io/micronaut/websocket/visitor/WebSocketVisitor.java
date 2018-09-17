/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.websocket.visitor;

import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.visitor.*;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;

import java.util.*;

/**
 * A {@link io.micronaut.inject.visitor.TypeElementVisitor} that validates WebSocket implementations at compile time.
 *
 * @author graemerocher
 * @since 1.0
 */
public class WebSocketVisitor implements TypeElementVisitor<WebSocketComponent, WebSocketMapping> {

    private Map<String, UriMatchTemplate> uriCache = new HashMap<>(3);

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        String uri = element.getValue(WebSocketComponent.class, String.class).orElse(WebSocketComponent.DEFAULT_URI);
        UriMatchTemplate template = uriCache.computeIfAbsent(uri, UriMatchTemplate::of);
        List<String> variables = template.getVariables();
        ParameterElement[] parameters = element.getParameters();
        if (ArrayUtils.isNotEmpty(parameters)) {

            if (element.isAnnotationPresent(OnOpen.class)) {
                for (ParameterElement parameter : parameters) {
                    if (isInvalidParameter(variables, parameter, WebSocketSession.class)) {
                        context.fail("Parameter to @OnOpen must either be a URI variable, a WebSocketSession or annotated with an HTTP binding annotation (such as @Header)", parameter);
                        break;
                    }
                }
            } else if (element.isAnnotationPresent(OnClose.class)) {
                for (ParameterElement parameter : parameters) {
                    if (isInvalidParameter(variables, parameter, WebSocketSession.class, CloseReason.class)) {
                        context.fail("Parameter to @OnClose must either be a URI variable, a CloseReason, a WebSocketSession or annotated with an HTTP binding annotation (such as @Header)", parameter);
                        break;
                    }
                }
            } else if (element.isAnnotationPresent(OnError.class)) {
                for (ParameterElement parameter : parameters) {
                    if (isInvalidParameter(variables, parameter, WebSocketSession.class, Throwable.class)) {
                        context.fail("Parameter to @OnError must either be a URI variable, a Throwable, a WebSocketSession or annotated with an HTTP binding annotation (such as @Header)", parameter);
                        break;
                    }
                }
            } else if (element.isAnnotationPresent(OnMessage.class)) {
                List<ParameterElement> bodyParameters = new ArrayList<>(3);
                for (ParameterElement parameter : parameters) {
                    if (isInvalidParameter(variables, parameter, WebSocketSession.class)) {
                        // potential body parameter
                        bodyParameters.add(parameter);
                    }
                }

                if (bodyParameters.size() > 1) {
                    context.fail("WebSocket @OnMessage handler has multiple possible message body arguments. : " + bodyParameters, element);
                }
            }
        }

    }

    private boolean isInvalidParameter(List<String> variables, ParameterElement parameter, Class... validTypes) {
        String parameterName = parameter.getName();
        ClassElement parameterType = parameter.getType();

        return !parameter.hasStereotype(Bindable.class) && !variables.contains(parameterName) && (parameterType == null || Arrays.stream(validTypes).noneMatch(parameterType::isAssignable));
    }
}
