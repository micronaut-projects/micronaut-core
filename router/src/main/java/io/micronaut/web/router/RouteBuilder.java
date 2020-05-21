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
package io.micronaut.web.router;

import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.MethodConvention;
import io.micronaut.core.naming.conventions.PropertyConvention;
import io.micronaut.core.naming.conventions.TypeConvention;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.UriMapping;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ProxyBeanDefinition;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * <p>An interface for classes capable of building HTTP routing information.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("MethodName")
@Indexed(RouteBuilder.class)
public interface RouteBuilder {

    /**
     * Used to signify to the route that the ID of the resource is used.
     */
    PropertyConvention ID = PropertyConvention.ID;

    /**
     * @return The exposed ports
     */
    Set<Integer> getExposedPorts();

    /**
     * @return The filter routes
     */
    List<FilterRoute> getFilterRoutes();

    /**
     * @return Obtain a list of constructed routes
     */
    List<UriRoute> getUriRoutes();

    /**
     * @return Obtain a list of constructed routes
     */
    List<StatusRoute> getStatusRoutes();

    /**
     * @return Obtain a list of constructed routes
     */
    List<ErrorRoute> getErrorRoutes();

    /**
     * @return The URI naming strategy
     */
    UriNamingStrategy getUriNamingStrategy();

    /**
     * Add a filter.
     *
     * @param pathPattern The path pattern for the filter
     * @param filter      The filter itself
     * @return The {@link FilterRoute}
     */
    FilterRoute addFilter(String pathPattern, Supplier<HttpFilter> filter);

    /**
     * Add a filter.
     *
     * @param pathPattern The path pattern for the filter
     * @param beanLocator The bean locator
     * @param beanDefinition The bean definition
     * @return The {@link FilterRoute}
     * @since 2.0
     */
    FilterRoute addFilter(String pathPattern, BeanLocator beanLocator, BeanDefinition<? extends HttpFilter> beanDefinition);

    /**
     * <p>Builds the necessary mappings to treat the given class as a REST endpoint.</p>
     * <p>
     * <p>For example given a class called BookController the following routes will be produced:</p>
     * <p>
     * <pre>{@code
     *     GET "/book"
     *     GET "/book{/id}"
     *     POST "/book"
     *     PUT "/book{/id}"
     *     PATCH "/book{/id}"
     *     DELETE "/book{/id}"
     * }</pre>
     * <p>
     * <p>By default it is assumed the accepted and returned content type is
     * {@link io.micronaut.http.MediaType#APPLICATION_JSON_TYPE}.</p>
     *
     * @param cls The class
     * @return The {@link ResourceRoute}
     */
    ResourceRoute resources(Class cls);

    /**
     * <p>Builds the necessary mappings to treat the given instance as a REST endpoint.</p>
     *
     * @param instance The instance
     * @return The {@link ResourceRoute}
     * @see #resources(Class)
     */
    default ResourceRoute resources(Object instance) {
        return resources(instance.getClass());
    }

    /**
     * <p>Builds the necessary mappings to treat the given class as a singular REST endpoint.</p>
     * <p>
     * <p>For example given a class called BookController the following routes will be produced:</p>
     * <p>
     * <pre>{@code
     *     GET "/book"
     *     POST "/book"
     *     PUT "/book"
     *     PATCH "/book"
     *     DELETE "/book"
     * }</pre>
     * <p>
     * <p>By default it is assumed the accepted and returned content type is
     * {@link io.micronaut.http.MediaType#APPLICATION_JSON_TYPE}.</p>
     *
     * @param cls The class
     * @return The {@link ResourceRoute}
     */
    ResourceRoute single(Class cls);

    /**
     * <p>Builds the necessary mappings to treat the given instance as a singular REST endpoint.</p>
     *
     * @param instance The instance
     * @return The {@link ResourceRoute}
     * @see #single(Class)
     */
    default ResourceRoute single(Object instance) {
        return single(instance.getClass());
    }

    /**
     * Register a route to handle the returned status code.
     *
     * @param status   The status code
     * @param instance The instance
     * @param method   The method
     * @return The route
     */
    default StatusRoute status(HttpStatus status, Object instance, String method) {
        return status(status, instance.getClass(), method, ReflectionUtils.EMPTY_CLASS_ARRAY);
    }

    /**
     * Register a route to handle the returned status code.
     *
     * @param status         The status code
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    StatusRoute status(HttpStatus status, Class type, String method, Class... parameterTypes);

    /**
     * Register a route to handle the returned status code. This implementation considers the originatingClass for matching.
     *
     * @param originatingClass The class where the error originates from
     * @param status           The status code
     * @param type             The type
     * @param method           The method
     * @param parameterTypes   The parameter types for the target method
     * @return The route
     */
    StatusRoute status(Class originatingClass, HttpStatus status, Class type, String method, Class... parameterTypes);

    /**
     * Register a route to handle the error.
     *
     * @param error          The error
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    ErrorRoute error(Class<? extends Throwable> error, Class type, String method, Class... parameterTypes);

    /**
     * Register a route to handle the error.
     *
     * @param originatingClass The class where the error originates from
     * @param error            The error type
     * @param type             The type to route to
     * @param method           The method THe method to route to
     * @param parameterTypes   The parameter types for the target method
     * @return The route
     */
    ErrorRoute error(Class originatingClass, Class<? extends Throwable> error, Class type, String method, Class... parameterTypes);

    /**
     * Register a route to handle the error.
     *
     * @param error The error
     * @param type  The type
     * @return The route
     */
    default ErrorRoute error(Class<? extends Throwable> error, Class type) {
        return error(error, type, NameUtils.decapitalize(NameUtils.trimSuffix(type.getSimpleName(), "Exception", "Error")), ReflectionUtils.EMPTY_CLASS_ARRAY);
    }

    /**
     * Register a route to handle the error.
     *
     * @param error    The error
     * @param instance The instance
     * @return The route
     */
    default ErrorRoute error(Class<? extends Throwable> error, Object instance) {
        return error(
                error,
                instance.getClass(),
                NameUtils.decapitalize(NameUtils.trimSuffix(error.getSimpleName(), "Exception", "Error")),
                error);
    }

    /**
     * Register a route to handle the error.
     *
     * @param error    The error
     * @param instance The instance
     * @param method   The method
     * @return The route
     */
    default ErrorRoute error(Class<? extends Throwable> error, Object instance, String method) {
        return error(error, instance.getClass(), method, error);
    }

    /**
     * Register a route to handle the error.
     *
     * @param error          The error
     * @param instance       The instance
     * @param method         The method
     * @param parameterTypes The parameter types
     * @return The route
     */
    default ErrorRoute error(Class<? extends Throwable> error, Object instance, String method, Class... parameterTypes) {
        return error(error, instance.getClass(), method, parameterTypes);
    }

    /**
     * Route the specified URI to the specified target for an HTTP GET. Since the method to execute is not
     * specified "index" is used by default.
     *
     * @param uri    The URI
     * @param target The target object
     * @return The route
     */
    default UriRoute GET(String uri, Object target) {
        return GET(uri, target, MethodConvention.INDEX.methodName());
    }

    /**
     * <p>Route to the specified object. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @return The route
     */
    default UriRoute GET(Object target) {
        Class<?> type = target.getClass();
        return GET(getUriNamingStrategy().resolveUri(type), target);
    }

    /**
     * <p>Route to the specified object and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @param id     The route id
     * @return The route
     */
    default UriRoute GET(Object target, PropertyConvention id) {
        Class<?> type = target.getClass();
        return GET(getUriNamingStrategy().resolveUri(type, id), target, MethodConvention.SHOW.methodName(), Object.class);
    }

    /**
     * <p>Route to the specified class. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @return The route
     */
    default UriRoute GET(Class type) {
        return GET(getUriNamingStrategy().resolveUri(type), type, MethodConvention.INDEX.methodName());
    }

    /**
     * <p>Route to the specified class and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @param id   The route id
     * @return The route
     */
    default UriRoute GET(Class type, PropertyConvention id) {
        return GET(getUriNamingStrategy().resolveUri(type, id), type, MethodConvention.SHOW.methodName(), Object.class);
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri    The URI
     * @param method The method
     * @return The route
     */
    default UriRoute GET(String uri, ExecutableMethod<?, ?> method) {
        return GET(uri, method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param beanDefinition The bean definition
     * @param uri            The URI
     * @param method         The method
     * @return The route
     */
    default UriRoute GET(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return GET(uri, beanDefinition.getBeanType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param target         The target
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute GET(String uri, Object target, String method, Class... parameterTypes);

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute GET(String uri, Class<?> type, String method, Class... parameterTypes);

    /**
     * Route the specified URI to the specified target for an HTTP POST. Since the method to execute is not
     * specified "index" is used by default.
     *
     * @param uri            The URI
     * @param target         The target object
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    default UriRoute POST(String uri, Object target, Class... parameterTypes) {
        return POST(uri, target, MethodConvention.SAVE.methodName(), parameterTypes);
    }

    /**
     * <p>Route to the specified object. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @return The route
     */
    default UriRoute POST(Object target) {
        Class<?> type = target.getClass();
        return POST(getUriNamingStrategy().resolveUri(type), target);
    }

    /**
     * <p>Route to the specified object and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @param id     The route id
     * @return The route
     */
    default UriRoute POST(Object target, PropertyConvention id) {
        Class<?> type = target.getClass();
        return POST(getUriNamingStrategy().resolveUri(type, id), target, MethodConvention.UPDATE.methodName());
    }

    /**
     * <p>Route to the specified class. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @return The route
     */
    default UriRoute POST(Class type) {
        return POST(getUriNamingStrategy().resolveUri(type), type, MethodConvention.SAVE.methodName());
    }

    /**
     * <p>Route to the specified class and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @param id   The route id
     * @return The route
     */
    default UriRoute POST(Class type, PropertyConvention id) {
        return POST(getUriNamingStrategy().resolveUri(type, id), type, MethodConvention.UPDATE.methodName());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri    The URI
     * @param method The method
     * @return The route
     */
    default UriRoute POST(String uri, ExecutableMethod<?, ?> method) {
        return POST(uri, method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param beanDefinition The bean definition
     * @param uri            The URI
     * @param method         The method
     * @return The route
     */
    default UriRoute POST(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return POST(uri, beanDefinition.getBeanType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param target         The target
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute POST(String uri, Object target, String method, Class... parameterTypes);

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute POST(String uri, Class type, String method, Class... parameterTypes);

    /**
     * Route the specified URI to the specified target for an HTTP PUT. Since the method to execute is not
     * specified "index" is used by default.
     *
     * @param uri    The URI
     * @param target The target object
     * @return The route
     */
    default UriRoute PUT(String uri, Object target) {
        return PUT(uri, target, MethodConvention.UPDATE.methodName());
    }

    /**
     * <p>Route to the specified object. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @return The route
     */
    default UriRoute PUT(Object target) {
        Class<?> type = target.getClass();
        return PUT(getUriNamingStrategy().resolveUri(type), target);
    }

    /**
     * <p>Route to the specified object and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @param id     The route id
     * @return The route
     */
    default UriRoute PUT(Object target, PropertyConvention id) {
        Class<?> type = target.getClass();
        return PUT(getUriNamingStrategy().resolveUri(type, id), target, MethodConvention.UPDATE.methodName(), Object.class);
    }

    /**
     * <p>Route to the specified class. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @return The route
     */
    default UriRoute PUT(Class type) {
        return PUT(getUriNamingStrategy().resolveUri(type), type, MethodConvention.UPDATE.methodName(), Object.class);
    }

    /**
     * <p>Route to the specified class and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @param id   The route id
     * @return The route
     */
    default UriRoute PUT(Class type, PropertyConvention id) {
        return PUT(getUriNamingStrategy().resolveUri(type, id), type, MethodConvention.UPDATE.methodName(), Object.class);
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri    The URI
     * @param method The method
     * @return The route
     */
    default UriRoute PUT(String uri, ExecutableMethod<?, ?> method) {
        return PUT(uri, method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param beanDefinition The bean definition
     * @param uri            The URI
     * @param method         The method
     * @return The route
     */
    default UriRoute PUT(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return PUT(uri, beanDefinition.getBeanType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param target         The target
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute PUT(String uri, Object target, String method, Class... parameterTypes);

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute PUT(String uri, Class type, String method, Class... parameterTypes);

    /**
     * Route the specified URI to the specified target for an HTTP PATCH. Since the method to execute is not
     * specified "index" is used by default.
     *
     * @param uri    The URI
     * @param target The target object
     * @return The route
     */
    default UriRoute PATCH(String uri, Object target) {
        return PATCH(uri, target, MethodConvention.UPDATE.methodName());
    }

    /**
     * <p>Route to the specified object. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @return The route
     */
    default UriRoute PATCH(Object target) {
        Class<?> type = target.getClass();
        return PATCH(getUriNamingStrategy().resolveUri(type), target);
    }

    /**
     * <p>Route to the specified object and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @param id     The route id
     * @return The route
     */
    default UriRoute PATCH(Object target, PropertyConvention id) {
        Class<?> type = target.getClass();
        return PATCH(getUriNamingStrategy().resolveUri(type, id), target, MethodConvention.UPDATE.methodName(), Object.class);
    }

    /**
     * <p>Route to the specified class. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @return The route
     */
    default UriRoute PATCH(Class type) {
        return PATCH(getUriNamingStrategy().resolveUri(type), type, MethodConvention.UPDATE.methodName(), Object.class);
    }

    /**
     * <p>Route to the specified class and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @param id   The route id
     * @return The route
     */
    default UriRoute PATCH(Class type, PropertyConvention id) {
        return PATCH(getUriNamingStrategy().resolveUri(type, id), type, MethodConvention.UPDATE.methodName(), Object.class);
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri    The URI
     * @param method The method
     * @return The route
     */
    default UriRoute PATCH(String uri, ExecutableMethod<?, ?> method) {
        return PATCH(uri, method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param beanDefinition The bean definition
     * @param uri            The URI
     * @param method         The method
     * @return The route
     */
    default UriRoute PATCH(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return PATCH(uri, beanDefinition.getBeanType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param target         The target
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute PATCH(String uri, Object target, String method, Class... parameterTypes);

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute PATCH(String uri, Class type, String method, Class... parameterTypes);

    /**
     * Route the specified URI to the specified target for an HTTP DELETE. Since the method to execute is not
     * specified "index" is used by default.
     *
     * @param uri    The URI
     * @param target The target object
     * @return The route
     */
    default UriRoute DELETE(String uri, Object target) {
        return DELETE(uri, target, MethodConvention.DELETE.methodName(), Object.class);
    }

    /**
     * <p>Route to the specified object. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @return The route
     */
    default UriRoute DELETE(Object target) {
        Class<?> type = target.getClass();
        return DELETE(getUriNamingStrategy().resolveUri(type), target);
    }

    /**
     * <p>Route to the specified object and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @param id     The route id
     * @return The route
     */
    default UriRoute DELETE(Object target, PropertyConvention id) {
        Class<?> type = target.getClass();
        return DELETE(getUriNamingStrategy().resolveUri(type, id), target, MethodConvention.DELETE.methodName(), Object.class);
    }

    /**
     * <p>Route to the specified class. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @return The route
     */
    default UriRoute DELETE(Class type) {
        return DELETE(getUriNamingStrategy().resolveUri(type), type, MethodConvention.DELETE.methodName(), Object.class);
    }

    /**
     * <p>Route to the specified class and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @param id   The route id
     * @return The route
     */
    default UriRoute DELETE(Class type, PropertyConvention id) {
        return DELETE(getUriNamingStrategy().resolveUri(type, id), type, MethodConvention.DELETE.methodName(), Object.class);
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri    The URI
     * @param method The method
     * @return The route
     */
    default UriRoute DELETE(String uri, ExecutableMethod<?, ?> method) {
        return DELETE(uri, method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param beanDefinition The bean definition
     * @param uri            The URI
     * @param method         The method
     * @return The route
     */
    default UriRoute DELETE(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return DELETE(uri, beanDefinition.getBeanType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param target         The target
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute DELETE(String uri, Object target, String method, Class... parameterTypes);

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute DELETE(String uri, Class type, String method, Class... parameterTypes);

    /**
     * Route the specified URI to the specified target for an HTTP OPTIONS. Since the method to execute is not
     * specified "index" is used by default.
     *
     * @param uri    The URI
     * @param target The target object
     * @return The route
     */
    default UriRoute OPTIONS(String uri, Object target) {
        return OPTIONS(uri, target, MethodConvention.OPTIONS.methodName());
    }

    /**
     * <p>Route to the specified object. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @return The route
     */
    default UriRoute OPTIONS(Object target) {
        Class<?> type = target.getClass();
        return OPTIONS(getUriNamingStrategy().resolveUri(type), target);
    }

    /**
     * <p>Route to the specified object and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @param id     The route id
     * @return The route
     */
    default UriRoute OPTIONS(Object target, PropertyConvention id) {
        Class<?> type = target.getClass();
        return OPTIONS(getUriNamingStrategy().resolveUri(type, id), target, MethodConvention.OPTIONS.methodName());
    }

    /**
     * <p>Route to the specified class. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @return The route
     */
    default UriRoute OPTIONS(Class type) {
        return OPTIONS(getUriNamingStrategy().resolveUri(type), type, MethodConvention.OPTIONS.methodName());
    }

    /**
     * <p>Route to the specified class and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @param id   The route id
     * @return The route
     */
    default UriRoute OPTIONS(Class type, PropertyConvention id) {
        return OPTIONS(getUriNamingStrategy().resolveUri(type, id), type, MethodConvention.OPTIONS.methodName());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri    The URI
     * @param method The method
     * @return The route
     */
    default UriRoute OPTIONS(String uri, ExecutableMethod<?, ?> method) {
        return OPTIONS(uri, method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param beanDefinition The bean definition
     * @param uri            The URI
     * @param method         The method
     * @return The route
     */
    default UriRoute OPTIONS(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return OPTIONS(uri, beanDefinition.getBeanType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param target         The target
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute OPTIONS(String uri, Object target, String method, Class... parameterTypes);

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute OPTIONS(String uri, Class type, String method, Class... parameterTypes);

    /**
     * Route the specified URI to the specified target for an HTTP GET. Since the method to execute is not
     * specified "index" is used by default.
     *
     * @param uri    The URI
     * @param target The target object
     * @return The route
     */
    default UriRoute HEAD(String uri, Object target) {
        return HEAD(uri, target, MethodConvention.HEAD.methodName());
    }

    /**
     * <p>Route to the specified object. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @return The route
     */
    default UriRoute HEAD(Object target) {
        Class<?> type = target.getClass();
        return HEAD(getUriNamingStrategy().resolveUri(type), target);
    }

    /**
     * <p>Route to the specified object and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @param id     The route id
     * @return The route
     */
    default UriRoute HEAD(Object target, PropertyConvention id) {
        Class<?> type = target.getClass();
        return HEAD(getUriNamingStrategy().resolveUri(type, id), target, MethodConvention.HEAD.methodName());
    }

    /**
     * <p>Route to the specified class. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @return The route
     */
    default UriRoute HEAD(Class type) {
        return HEAD(getUriNamingStrategy().resolveUri(type), type, MethodConvention.HEAD.methodName());
    }

    /**
     * <p>Route to the specified class and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @param id   The route id
     * @return The route
     */
    default UriRoute HEAD(Class type, PropertyConvention id) {
        return HEAD(getUriNamingStrategy().resolveUri(type, id), type, MethodConvention.HEAD.methodName());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri    The URI
     * @param method The method
     * @return The route
     */
    default UriRoute HEAD(String uri, ExecutableMethod<?, ?> method) {
        return HEAD(uri, method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param beanDefinition The bean definition
     * @param uri            The URI
     * @param method         The method
     * @return The route
     */
    default UriRoute HEAD(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return HEAD(uri, beanDefinition.getBeanType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param target         The target
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute HEAD(String uri, Object target, String method, Class... parameterTypes);

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute HEAD(String uri, Class type, String method, Class... parameterTypes);

    /**
     * Route the specified URI to the specified target for an HTTP GET. Since the method to execute is not
     * specified "index" is used by default.
     *
     * @param uri    The URI
     * @param target The target object
     * @return The route
     */
    default UriRoute TRACE(String uri, Object target) {
        return TRACE(uri, target, MethodConvention.TRACE.methodName());
    }

    /**
     * <p>Route to the specified object. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @return The route
     */
    default UriRoute TRACE(Object target) {
        Class<?> type = target.getClass();
        return TRACE(getUriNamingStrategy().resolveUri(type), target);
    }

    /**
     * <p>Route to the specified object and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param target The object
     * @param id     The route id
     * @return The route
     */
    default UriRoute TRACE(Object target, PropertyConvention id) {
        Class<?> type = target.getClass();
        return TRACE(getUriNamingStrategy().resolveUri(type, id), target, MethodConvention.TRACE.methodName());
    }

    /**
     * <p>Route to the specified class. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @return The route
     */
    default UriRoute TRACE(Class type) {
        return TRACE(getUriNamingStrategy().resolveUri(type), type, MethodConvention.TRACE.methodName());
    }

    /**
     * <p>Route to the specified class and ID. The URI route is built by the configured {@link UriNamingStrategy}.</p>
     *
     * @param type The class
     * @param id   The route id
     * @return The route
     */
    default UriRoute TRACE(Class type, PropertyConvention id) {
        return HEAD(getUriNamingStrategy().resolveUri(type, id), type, MethodConvention.TRACE.methodName());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments.</p>
     *
     * @param uri    The URI
     * @param method The method
     * @return The route
     */
    default UriRoute TRACE(String uri, ExecutableMethod<?, ?> method) {
        return TRACE(uri, method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param beanDefinition The bean definition
     * @param uri            The URI
     * @param method         The method
     * @return The route
     */
    default UriRoute TRACE(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return TRACE(uri, beanDefinition.getBeanType(), method.getMethodName(), method.getArgumentTypes());
    }

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param target         The target
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute TRACE(String uri, Object target, String method, Class... parameterTypes);

    /**
     * <p>Route the specified URI template to the specified target.</p>
     * <p>
     * <p>The number of variables in the template should match the number of method arguments</p>
     *
     * @param uri            The URI
     * @param type           The type
     * @param method         The method
     * @param parameterTypes The parameter types for the target method
     * @return The route
     */
    UriRoute TRACE(String uri, Class type, String method, Class... parameterTypes);

    /**
     * <p>A URI naming strategy is used to dictate the default name to use when building a URI for a class.</p>
     * <p>
     * <p>The default strategy is as follows:</p>
     * <p>
     * <ul>
     * <li>{@link #resolveUri(Class)} - Where type is <code>example.BookController</code> value is <code>/book</code></li>
     * <li>{@link #resolveUri(Class, PropertyConvention)} - Where type is <code>example.BookController</code> value is <code>/book{/id}</code></li>
     * </ul>
     * <p>
     * <p>Implementers can override to provide other strategies such as pluralization etc.</p>
     */
    interface UriNamingStrategy {
        /**
         * Resolve the URI to use for the given type.
         *
         * @param type The type
         * @return The URI to use
         */
        default String resolveUri(Class<?> type) {
            Controller annotation = type.getAnnotation(Controller.class);
            String uri = normalizeUri(annotation != null ? annotation.value() : null);
            if (uri != null) {
                return uri;
            }
            return '/' + TypeConvention.CONTROLLER.asPropertyName(type);
        }

        /**
         * Resolve the URI to use for the given type.
         *
         * @param beanDefinition The type
         * @return The URI to use
         */
        default @NonNull
        String resolveUri(BeanDefinition<?> beanDefinition) {
            String uri = beanDefinition.stringValue(UriMapping.class).orElseGet(() ->
                    beanDefinition.stringValue(Controller.class).orElse(UriMapping.DEFAULT_URI)
            );
            uri = normalizeUri(uri);
            if (uri != null) {
                return uri;
            }
            Class<?> beanType;
            if (beanDefinition instanceof ProxyBeanDefinition) {
                ProxyBeanDefinition pbd = (ProxyBeanDefinition) beanDefinition;
                beanType = pbd.getTargetType();
            } else {
                beanType = beanDefinition.getBeanType();
            }
            return '/' + TypeConvention.CONTROLLER.asPropertyName(beanType);
        }

        /**
         * Resolve the URI to use for the given type.
         *
         * @param property The property
         * @return The URI to use
         */
        default @NonNull
        String resolveUri(String property) {
            if (StringUtils.isEmpty(property)) {
                return "/";
            }
            if (property.charAt(0) != '/') {
                return '/' + NameUtils.decapitalize(property);
            }
            return property;
        }

        /**
         * Resolve the URI to use for the given type and route id.
         *
         * @param type The type
         * @param id   the route id
         * @return The URI to use
         */
        default @NonNull String resolveUri(Class type, PropertyConvention id) {
            return resolveUri(type) + "/{" + id.lowerCaseName() + "}";
        }

        /**
         * Normalizes a URI.
         *
         * Ensures the string:
         * 1) Does not end with a /
         * 2) Starts with a /
         *
         * @param uri The URI
         * @return The normalized URI or null
         */
        default String normalizeUri(@Nullable String uri) {
            if (uri != null) {
                int len = uri.length();
                if (len > 0 && uri.charAt(0) != '/') {
                    uri = '/' + uri;
                }
                if (len > 1 && uri.charAt(uri.length() - 1) == '/') {
                    uri = uri.substring(0, uri.length() - 1);
                }
                if (len > 0) {
                    return uri;
                }
            }
            return null;
        }
    }
}
