/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpResponse;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.RequestHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HandlerMethodTargetMethodTest {

    @Test
    void aHandlerFunctionHasNoJavaMethod() {
        HandlerMethod<HttpResponse<?>> handler = HandlerMethod.of((RequestHandler) (request, pathVariables) -> HttpResponse.ok());

        assertThrows(UnsupportedOperationException.class, handler::getTargetMethod);
        assertEquals("handle", handler.getMethodName());
    }

    @Test
    void aRouteThatImplementsABeanMethodHasItsTargetMethod() throws NoSuchMethodException {
        Method status = Service.class.getMethod("status");
        HandlerMethod<HttpResponse<?>> handler = HandlerMethod.of((RequestHandler) (request, pathVariables) -> HttpResponse.ok());

        handler.annotationMetadata(new ServiceStatus(status));

        assertEquals(status, handler.getTargetMethod());
        assertEquals(Service.class, handler.getDeclaringType());
        assertEquals("status", handler.getMethodName());
    }

    public static final class Service {
        public String status() {
            return "up";
        }
    }

    private record ServiceStatus(Method method) implements ExecutableMethod<Service, String> {
        @Override
        public Method getTargetMethod() {
            return method;
        }

        @Override
        public ReturnType<String> getReturnType() {
            return ReturnType.of(String.class);
        }

        @Override
        public String invoke(Service instance, Object... arguments) {
            return instance.status();
        }

        @Override
        public Class<Service> getDeclaringType() {
            return Service.class;
        }

        @Override
        public String getMethodName() {
            return "status";
        }

        @Override
        public Argument<?>[] getArguments() {
            return Argument.ZERO_ARGUMENTS;
        }
    }
}
