package io.micronaut.http.server.util.localeresolution

import io.micronaut.context.ApplicationContext
import io.micronaut.core.order.OrderUtil
import spock.lang.Specification

class LocaleResolutionOrderSpec extends Specification {
    void "test oder of included locale resolvers"() {
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.server.locale-resolution.cookie-name': 'Locale',
                'micronaut.server.locale-resolution.fixed': 'ko-KR'
        ])

        when:
        Collection<HttpLocaleResolver> localeResolvers = applicationContext.getBeansOfType(HttpLocaleResolver)
        OrderUtil.sort(localeResolvers)

        then:
        localeResolvers
        localeResolvers.size() == 4
        localeResolvers[0] instanceof HttpFixedLocaleResolver
        localeResolvers[1] instanceof CookieLocaleResolver
        localeResolvers[2] instanceof CompositeHttpLocaleResolver
        localeResolvers[3] instanceof RequestLocaleResolver

        cleanup:
        applicationContext.close()
    }
}
