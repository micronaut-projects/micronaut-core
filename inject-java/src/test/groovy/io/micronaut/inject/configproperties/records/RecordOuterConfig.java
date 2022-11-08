package io.micronaut.inject.configproperties.records;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import java.util.List;

@ConfigurationProperties("test")
public record RecordOuterConfig(
    String name,
    int age,
    RecordInnerConfig inner,
    List<RecordInners> inners
) {

    @ConfigurationProperties("inner")
    record RecordInnerConfig(String foo, ThirdLevel thirdLevel) {

        @ConfigurationProperties("nested")
        record ThirdLevel(int num) {}
    }

    @EachProperty("inners")
    record RecordInners(@Parameter String name, int count, ThirdLevel thirdLevel) {

        @ConfigurationProperties("nested")
        record ThirdLevel(int num) {}
    }
}
