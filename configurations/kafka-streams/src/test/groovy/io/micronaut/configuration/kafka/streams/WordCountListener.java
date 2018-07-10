package io.micronaut.configuration.kafka.streams;

import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.Topic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@KafkaListener(offsetReset = OffsetReset.EARLIEST)
public class WordCountListener {

    private final Map<String, LongAdder> wordCounts = new ConcurrentHashMap<>();

    @Topic(WordCountStream.OUTPUT)
    void count(@KafkaKey String word, long count) {
        wordCounts.computeIfAbsent(word, k -> new LongAdder()).add(count);
    }

    public long getCount(String word) {
        LongAdder longAdder = wordCounts.get(word);
        if (longAdder != null) {
            return longAdder.longValue();
        }
        return 0;
    }
}
