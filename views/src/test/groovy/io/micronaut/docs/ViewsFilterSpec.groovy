/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.views.handlebars.HandlebarsViewsRenderer
import io.micronaut.views.ViewsFilter
import io.micronaut.views.thymeleaf.ThymeleafViewsRenderer
import io.micronaut.views.velocity.VelocityViewsRenderer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ViewsFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'spec.name': 'templatesfilter',
                    'micronaut.views.enabled': true,
                    'micronaut.views.thymeleaf.enabled': false,
                    'micronaut.views.handlebars.enabled': false,
                    'micronaut.views.velocity.enabled': false,
                    'micronaut.views.freemarker.enabled': false,
            ],
            "test")

    def "TemplatesFilter is not loaded unless bean TemplateRenderer exists"() {
        when:
        embeddedServer.applicationContext.getBean(VelocityViewsRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(HandlebarsViewsRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(ThymeleafViewsRenderer)

        then:
        thrown(NoSuchBeanException)

        when:
        embeddedServer.applicationContext.getBean(ViewsFilter)

        then:
        thrown(NoSuchBeanException)

    }
}
