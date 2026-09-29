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
package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class EnumKeyedMapConfigurationPropertiesSpec extends Specification {

    void 'test an enum-keyed Map of @EachProperty beans is populated from configuration'() {
        given: 'the reproducer from #12477'
        ApplicationContext context = buildContext('''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention
import io.micronaut.core.value.PropertyCatalog

@ConfigurationProperties("service")
data class AppConfig(

    val countries: Map<Country, CountryConfig>,

    @param:MapFormat(keyFormat = StringConvention.RAW)
    val countries2: Map<Country, Int>
)

@EachProperty(value = "service.countries", catalog = PropertyCatalog.RAW)
data class CountryConfig(
    val population: Int,
    val area: Int
)

enum class Country {
    France, UAE
}
''', true, [
                'service.countries.France.population': 69081996,
                'service.countries.France.area'      : 632702,
                'service.countries.UAE.population'   : 11027129,
                'service.countries.UAE.area'         : 83600,
                'service.countries2.France'          : 555,
                'service.countries2.UAE'             : 333
        ])
        Class<?> countryType = context.classLoader.loadClass('test.Country')
        def france = Enum.valueOf(countryType as Class<? extends Enum>, 'France')
        def uae = Enum.valueOf(countryType as Class<? extends Enum>, 'UAE')

        when:
        def config = context.getBean(context.classLoader.loadClass('test.AppConfig'))

        then: '''the enum-keyed Map of beans is populated, as the String-keyed and scalar variants
                 already were. Before the fix this failed with a NoSuchBeanException.'''
        config.countries.size() == 2
        config.countries.keySet().every { countryType.isInstance(it) }
        config.countries[france].population == 69081996
        config.countries[france].area == 632702
        config.countries[uae].population == 11027129
        config.countries[uae].area == 83600

        and: 'the enum-keyed scalar Map is unaffected'
        config.countries2[france] == 555
        config.countries2[uae] == 333

        cleanup:
        context.close()
    }
}
