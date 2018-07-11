package io.micronaut.configuration.kafka.streams;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

@Factory
public class WordCountStream {

    public static final String INPUT = "streams-plaintext-input";
    public static final String OUTPUT = "streams-wordcount-output";

    @Singleton
    KStream<String, String> wordCountStream(ConfiguredStreamBuilder builder) {
        // set default serdes
        Properties props = builder.getConfiguration();
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KStream<String, String> source = builder.stream(INPUT);
        KTable<String, Long> counts = source
                .flatMapValues( value -> Arrays.asList(value.toLowerCase(Locale.getDefault()).split(" ")))
                .groupBy((key, value) -> value)
                .count();

        // need to override value serde to Long type
        counts.toStream().to(OUTPUT, Produced.with(Serdes.String(), Serdes.Long()));
        return source;
    }
}
