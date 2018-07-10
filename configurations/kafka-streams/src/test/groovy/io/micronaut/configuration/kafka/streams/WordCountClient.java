package io.micronaut.configuration.kafka.streams;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;

@KafkaClient
public interface WordCountClient {

    @Topic(WordCountStream.INPUT)
    void publishSentence(@KafkaKey String key, String sentence);
}
