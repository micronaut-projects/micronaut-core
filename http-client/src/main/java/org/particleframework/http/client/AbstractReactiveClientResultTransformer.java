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
package org.particleframework.http.client;

import org.particleframework.core.convert.ConversionService;
import org.particleframework.discovery.exceptions.NoAvailableServiceException;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.inject.MethodExecutionHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Abstract version of {@link ReactiveClientResultTransformer}
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractReactiveClientResultTransformer implements ReactiveClientResultTransformer {
    protected static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);


    protected <T> T fallbackOr(Supplier<Optional<MethodExecutionHandle<Object>>> fallbackResolver, Throwable exception, T defaultValue, ExecutableMethod<Object, Object> invocation, Object... parameters) {

        if(exception instanceof NoAvailableServiceException) {
            NoAvailableServiceException nase = (NoAvailableServiceException) exception;
            if(LOG.isErrorEnabled()) {
                LOG.debug(nase.getMessage(), nase);
                LOG.error("HTTP @Client [{}] invocation not available [{}]. Attempting to resolve fallback for unavailable service [{}]", invocation.getDeclaringType().getName(), invocation, nase.getServiceID());
            }

        }
        else {
            if(LOG.isErrorEnabled()) {
                LOG.error("HTTP @Client ["+invocation.getDeclaringType().getName()+"] invocation ["+invocation+"] received error response: " + exception.getMessage(), exception);
            }
        }

        Optional<MethodExecutionHandle<Object>> fallback = fallbackResolver.get();
        if(fallback.isPresent()) {
            MethodExecutionHandle<Object> fallbackHandle = fallback.get();
            if(LOG.isDebugEnabled()) {
                LOG.debug("HTTP @Client [{}] invocation [{}] resolved fallback: {}", fallbackHandle.getDeclaringType(), invocation, fallbackHandle );
            }

            Object result;
            try {
                result = fallbackHandle.invoke(parameters);
            } catch (Exception e) {
                if(LOG.isErrorEnabled()) {
                    LOG.error("Error invoking HTTP client fallback ["+fallbackHandle+"]. Returning original error. Cause: " + e.getMessage(), e);
                }
                return defaultValue;
            }
            Optional<?> converted = ConversionService.SHARED.convert(result, fallbackHandle.getTargetMethod().getReturnType());
            if(converted.isPresent()) {
                return (T) converted.get();
            }
            else {
                if(LOG.isErrorEnabled()) {
                    LOG.error("HTTP @Client fallback [{}] invocation ["+fallbackHandle+"] returned invalid result: {}", fallbackHandle.getDeclaringType().getName(), fallbackHandle, result);
                }
                return defaultValue;
            }
        }
        else {
            return defaultValue;
        }
    }
}
