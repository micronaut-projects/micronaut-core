package io.micronaut.configuration.kafka.streams

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class KafkaStreamsSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            CollectionUtils.mapOf(
                    "kafka.bootstrap.servers", 'localhost:${random.port}',
                    AbstractKafkaConfiguration.EMBEDDED, true,
                    AbstractKafkaConfiguration.EMBEDDED_TOPICS, [WordCountStream.INPUT, WordCountStream.OUTPUT],
                    'kafka.streams.my-stream.num.stream.threads',10
            )
    )

    void "test config"() {
        expect:
        context.getBean(ConfiguredStreamBuilder, Qualifiers.byName('my-stream')).configuration['num.stream.threads'] == "10"
    }

    void "test kafka stream application"() {
        given:

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        when:
        WordCountClient wordCountClient = context.getBean(WordCountClient);
        wordCountClient.publishSentence("The quick brown fox jumps over the lazy dog. THE QUICK BROWN FOX JUMPED OVER THE LAZY DOG'S BACK");

        WordCountListener countListener = context.getBean(WordCountListener)

        then:
        conditions.eventually {
            countListener.getCount("fox") > 0
            countListener.getCount("jumps") > 0
            println countListener.wordCounts
        }

    }


}
