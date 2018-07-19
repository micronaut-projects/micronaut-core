package io.micronaut.configuration.kafka.streams;

import io.micronaut.configuration.kafka.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@KafkaListener(offsetReset = OffsetReset.EARLIEST)
public class WordCountListener {

    private final Map<String, Long> wordCounts = new ConcurrentHashMap<>();

    @Topic(WordCountStream.OUTPUT)
    void count(@KafkaKey String word, long count) {
        wordCounts.put(word, count);
    }

    public long getCount(String word) {
        Long num = wordCounts.get(word);
        if (num != null) {
            return num;
        }
        return 0;
    }

    public Map<String, Long> getWordCounts() {
        return Collections.unmodifiableMap(wordCounts);
    }
}
