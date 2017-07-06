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
package org.particleframework.web.router

import groovy.transform.CompileStatic
import org.particleframework.context.ApplicationContext
import org.particleframework.context.annotation.Bean
import org.particleframework.context.annotation.Factory
import org.particleframework.web.router.RouteBuilder.UriNamingStrategy

import javax.inject.Singleton

/**
 * <p>A factory that builds the default {@link Router}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
@CompileStatic
class RouterFactory {

    @Singleton
    @Bean
    Router router(RouteBuilder... routeBuilders) {
        new DefaultRouter(routeBuilders)
    }

    @Singleton
    @Bean
    UriNamingStrategy uriNamingStrategy() {
        DefaultRouteBuilder.CAMEL_CASE_NAMING_STRATEGY
    }

    @Singleton
    @Bean
    AnnotationRouteBuilder annotationRouteBuilder(ApplicationContext applicationContext, UriNamingStrategy uriNamingStrategy) {
        return new AnnotationRouteBuilder(applicationContext, uriNamingStrategy)
    }
}
