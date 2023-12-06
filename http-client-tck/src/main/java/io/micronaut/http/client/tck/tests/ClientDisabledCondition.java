/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.tck.tests;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

/**
 * A condition that enables or disables a test based on the presence of the {@link ClientDisabled} annotation.
 * <p>
 * The condition is enabled by default, unless the annotation is present and the {@link ClientDisabled#jdk()} or
 * {@link ClientDisabled#httpClient()} parameters match the current JDK version and the HTTP client configuration
 * parameter.
 */
public class ClientDisabledCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED_BY_DEFAULT = enabled("@ClientDisabled is not present");

    private static final ConditionEvaluationResult ENABLED = enabled("Enabled");

    private static final ConditionEvaluationResult DISABLED = disabled("Disabled");

    public static final String JDK = "jdk";
    public static final String NETTY = "netty";
    public static final String HTTP_CLIENT_CONFIGURATION = "httpClient";

    private static boolean jdkMajorVersionMatches(ClientDisabled d) {
        return Integer.toString(Runtime.version().feature()).equals(d.jdk());
    }

    private static boolean clientParameterMatches(ExtensionContext context, ClientDisabled d) {
        return context.getConfigurationParameter(HTTP_CLIENT_CONFIGURATION).orElse("").equalsIgnoreCase(d.httpClient());
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return findAnnotation(context.getElement(), ClientDisabled.class)
            .map(d -> clientParameterMatches(context, d) && jdkMajorVersionMatches(d)
                ? DISABLED
                : ENABLED)
            .orElse(ENABLED_BY_DEFAULT);
    }

    /**
     * Annotation that can be used to disable a test based on the JDK version and the HTTP client configuration.
     */
    @Documented
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(ClientDisabledCondition.class)
    public @interface ClientDisabled {

        String httpClient() default "";

        String jdk() default "";
    }
}
