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
package org.particleframework.http.binding

import groovy.transform.CompileStatic
import org.particleframework.context.annotation.Bean
import org.particleframework.context.annotation.Factory
import org.particleframework.core.convert.ConversionService
import org.particleframework.http.binding.binders.request.CookieAnnotationBinder
import org.particleframework.http.binding.binders.request.HeaderAnnotationBinder
import org.particleframework.http.binding.binders.request.ParameterAnnotationBinder
import org.particleframework.http.binding.binders.request.RequestArgumentBinder

import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
@CompileStatic
class RequestBinderRegistryFactory {

    @Singleton
    @Bean
    CookieAnnotationBinder cookieAnnotationBinder(ConversionService conversionService) {
        return new CookieAnnotationBinder(conversionService)
    }

    @Singleton
    @Bean
    HeaderAnnotationBinder headerAnnotationBinder(ConversionService conversionService) {
        return new HeaderAnnotationBinder(conversionService)
    }

    @Singleton
    @Bean
    ParameterAnnotationBinder parameterAnnotationBinder(ConversionService conversionService) {
        return new ParameterAnnotationBinder(conversionService)
    }

    @Singleton
    @Bean
    RequestBinderRegistry requestBinderRegistry(ConversionService conversionService, RequestArgumentBinder...binders) {
        return new DefaultRequestBinderRegistry(conversionService, binders)
    }
}
