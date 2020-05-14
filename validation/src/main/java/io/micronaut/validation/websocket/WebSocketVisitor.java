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
package io.micronaut.validation.websocket;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.*;
import io.micronaut.websocket.annotation.*;

import javax.annotation.processing.SupportedOptions;
import java.util.*;

/**
 * A {@link io.micronaut.inject.visitor.TypeElementVisitor} that validates WebSocket implementations at compile time.
 *
 * @author graemerocher
 * @since 1.0
 */
@SupportedOptions(WebSocketVisitor.VALIDATION_OPTION)
public class WebSocketVisitor implements TypeElementVisitor<WebSocketComponent, WebSocketMapping> {

    static final String VALIDATION_OPTION = "micronaut.websocket.validation";
    private static final String WEB_SOCKET_COMPONENT = "io.micronaut.websocket.annotation.WebSocketComponent";
    private static final String WEB_SOCKET_SESSION = "io.micronaut.websocket.WebSocketSession";
    private static final String HTTP_REQUEST = "io.micronaut.http.HttpRequest";
    private static final String CLOSE_REASON = "io.micronaut.websocket.CloseReason";
    private static final String ON_OPEN = "io.micronaut.websocket.annotation.OnOpen";
    private static final String ON_CLOSE = "io.micronaut.websocket.annotation.OnClose";
    private static final String ON_MESSAGE = "io.micronaut.websocket.annotation.OnMessage";
    private static final String ON_ERROR = "io.micronaut.websocket.annotation.OnError";

    private Map<String, UriMatchTemplate> uriCache = new HashMap<>(3);
    private boolean skipValidation = false;

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return CollectionUtils.setOf(
                WebSocketComponent.class.getName()
        );
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (skipValidation) {
            return;
        }
        String uri = element.stringValue(WEB_SOCKET_COMPONENT).orElse("/ws");
        UriMatchTemplate template = uriCache.computeIfAbsent(uri, UriMatchTemplate::of);
        List<String> variables = template.getVariableNames();
        ParameterElement[] parameters = element.getParameters();
        if (ArrayUtils.isNotEmpty(parameters)) {

            if (element.hasAnnotation(ON_OPEN)) {
                for (ParameterElement parameter : parameters) {
                    if (isInvalidParameter(variables, parameter, WEB_SOCKET_SESSION, HTTP_REQUEST)) {
                        context.fail("Parameter to @OnOpen must either be a URI variable, a WebSocketSession , the HttpRequest, or annotated with an HTTP binding annotation (such as @Header)", parameter);
                        break;
                    }
                }
            } else if (element.hasAnnotation(ON_CLOSE)) {
                for (ParameterElement parameter : parameters) {
                    if (isInvalidParameter(variables, parameter, WEB_SOCKET_SESSION, CLOSE_REASON)) {
                        context.fail("Parameter to @OnClose must either be a URI variable, a CloseReason, a WebSocketSession or annotated with an HTTP binding annotation (such as @Header)", parameter);
                        break;
                    }
                }
            } else if (element.hasAnnotation(ON_ERROR)) {
                for (ParameterElement parameter : parameters) {
                    if (isInvalidParameter(variables, parameter, WEB_SOCKET_SESSION, Throwable.class.getName())) {
                        context.fail("Parameter to @OnError must either be a URI variable, a Throwable, a WebSocketSession or annotated with an HTTP binding annotation (such as @Header)", parameter);
                        break;
                    }
                }
            } else if (element.hasAnnotation(ON_MESSAGE)) {
                List<ParameterElement> bodyParameters = new ArrayList<>(3);
                for (ParameterElement parameter : parameters) {
                    if (isInvalidParameter(variables, parameter, WEB_SOCKET_SESSION)) {
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

    @Override
    public void start(VisitorContext visitorContext) {
        String prop = visitorContext.getOptions().getOrDefault(VALIDATION_OPTION, "true");
        skipValidation = prop != null && prop.equals("false");
    }

    private boolean isInvalidParameter(List<String> variables, ParameterElement parameter, String... validTypes) {
        String parameterName = parameter.getName();
        ClassElement parameterType = parameter.getType();

        return !parameter.hasStereotype(Bindable.class) && !variables.contains(parameterName) && (parameterType == null || Arrays.stream(validTypes).noneMatch(parameterType::isAssignable));
    }
}
