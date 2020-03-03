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
package io.micronaut.web.router

import groovy.transform.InheritConstructors
import io.micronaut.core.naming.conventions.PropertyConvention
import io.micronaut.http.HttpStatus
import org.codehaus.groovy.runtime.MethodClosure

/**
 * <p>Enhancements to {@link DefaultRouteBuilder} for Groovy</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@InheritConstructors
class GroovyRouteBuilder extends DefaultRouteBuilder {

    // status handlers
    StatusRoute status(HttpStatus httpStatus, MethodClosure methodClosure) {
        status(httpStatus, methodClosure.owner.getClass(), methodClosure.method, methodClosure.parameterTypes)
    }

    ResourceRoute resources(Object target, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        resources(target).nest(nested)
    }

    // GET methods

    Route GET(Object target, PropertyConvention id, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        GET(target, id).nest(nested)
    }

    Route GET(Object target, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        GET(target).nest(nested)
    }

    Route GET(String uri, MethodClosure methodClosure) {
        GET(uri, methodClosure.owner, methodClosure.method, methodClosure.parameterTypes)
    }

    // POST methods

    Route POST(Object target, PropertyConvention id, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        POST(target, id).nest(nested)
    }

    Route POST(Object target, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        POST(target).nest(nested)
    }

    Route POST(String uri, MethodClosure methodClosure) {
        POST(uri, methodClosure.owner, methodClosure.method, methodClosure.parameterTypes)
    }

    // PUT methods

    Route PUT(Object target, PropertyConvention id, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        PUT(target, id).nest(nested)
    }

    Route PUT(Object target, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        PUT(target).nest(nested)
    }

    Route PUT(String uri, MethodClosure methodClosure) {
        PUT(uri, methodClosure.owner, methodClosure.method, methodClosure.parameterTypes)
    }

    // PATCH methods

    Route PATCH(Object target, PropertyConvention id, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        PATCH(target, id).nest(nested)
    }

    Route PATCH(Object target, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        PATCH(target).nest(nested)
    }

    Route PATCH(String uri, MethodClosure methodClosure) {
        PATCH(uri, methodClosure.owner, methodClosure.method, methodClosure.parameterTypes)
    }

    // DELETE methods

    Route DELETE(Object target, PropertyConvention id, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        DELETE(target, id).nest(nested)
    }

    Route DELETE(Object target, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        DELETE(target).nest(nested)
    }

    Route DELETE(String uri, MethodClosure methodClosure) {
        DELETE(uri, methodClosure.owner, methodClosure.method, methodClosure.parameterTypes)
    }

    // OPTIONS methods

    Route OPTIONS(Object target, PropertyConvention id, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        OPTIONS(target, id).nest(nested)
    }

    Route OPTIONS(Object target, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        OPTIONS(target).nest(nested)
    }

    Route OPTIONS(String uri, MethodClosure methodClosure) {
        OPTIONS(uri, methodClosure.owner, methodClosure.method, methodClosure.parameterTypes)
    }

    // HEAD methods

    Route HEAD(Object target, PropertyConvention id, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        HEAD(target, id).nest(nested)
    }

    Route HEAD(Object target, @DelegatesTo(GroovyRouteBuilder) Closure nested) {
        HEAD(target).nest(nested)
    }

    Route HEAD(String uri, MethodClosure methodClosure) {
        HEAD(uri, methodClosure.owner, methodClosure.method, methodClosure.parameterTypes)
    }
}
