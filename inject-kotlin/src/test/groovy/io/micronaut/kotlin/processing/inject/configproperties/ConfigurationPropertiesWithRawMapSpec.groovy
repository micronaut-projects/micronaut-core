package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ConfigurationPropertiesWithRawMapSpec extends Specification {

    void 'test that injected raw properties are correct'() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'jpa.properties.hibernate.fooBar':'good',
                'jpa.properties.hibernate.CAP':'whatever'
        )

        expect:"when using StringConvention.RAW the map is injected as is"
        context.getBean(MyHibernateConfig)
                .properties == ['hibernate.fooBar':'good', 'hibernate.CAP': 'whatever']

        and:"When not using StringConvention.RAW then you get the normalized versions"
        context.getBean(MyHibernateConfig2)
                .properties == ['hibernate.foo-bar':'good', 'hibernate.cap': 'whatever']
    }
}
