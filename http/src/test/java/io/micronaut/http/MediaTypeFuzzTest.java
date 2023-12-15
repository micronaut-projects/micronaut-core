package io.micronaut.http;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.util.ArrayList;
import java.util.List;

public class MediaTypeFuzzTest {
    @FuzzTest
    public void orderedOf(FuzzedDataProvider input) {
        List<String> strings = new ArrayList<>();
        while (input.remainingBytes() > 0 && strings.size() < 128) {
            strings.add(input.consumeString(32));
        }
        MediaType.orderedOf(strings);
    }
}
