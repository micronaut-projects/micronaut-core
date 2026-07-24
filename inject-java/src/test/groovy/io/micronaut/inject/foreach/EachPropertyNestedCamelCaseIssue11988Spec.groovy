package io.micronaut.inject.foreach

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Issue
import spock.lang.Specification

class EachPropertyNestedCamelCaseIssue11988Spec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11988")
    void "nested camelCase @ConfigurationProperties under @EachProperty(list=true)"() {
        given:
        def context = ApplicationContext.run(
                "foo.bar[0].bloomFilter.expectedSize": '1000',
                "foo.bar[0].bloomFilter.fpp": '0.01'
        )

        when:
        def bean = context.getBean(CamelCaseEachAggregatorConfig, Qualifiers.byName("0"))

        then:
        bean.bloomFilter.expectedSize == 1000
        bean.bloomFilter.fpp == 0.01d

        cleanup:
        context.close()
    }

    void "nested kebab-case @ConfigurationProperties under @EachProperty(list=true)"() {
        given:
        def context = ApplicationContext.run(
                "foo.bar[0].bloom-filter.expectedSize": '1000',
                "foo.bar[0].bloom-filter.fpp": '0.01'
        )

        when:
        def bean = context.getBean(KebabCaseEachAggregatorConfig, Qualifiers.byName("0"))

        then:
        bean.bloomFilter.expectedSize == 1000
        bean.bloomFilter.fpp == 0.01d

        cleanup:
        context.close()
    }

    @EachProperty(value = "foo.bar", list = true)
    static interface CamelCaseEachAggregatorConfig {
        CamelCaseBloomConfig getBloomFilter()

        @ConfigurationProperties("bloomFilter")
        static interface CamelCaseBloomConfig {
            int getExpectedSize()
            double getFpp()
        }
    }

    @EachProperty(value = "foo.bar", list = true)
    static interface KebabCaseEachAggregatorConfig {
        KebabCaseBloomConfig getBloomFilter()

        @ConfigurationProperties("bloom-filter")
        static interface KebabCaseBloomConfig {
            int getExpectedSize()
            double getFpp()
        }
    }
}
