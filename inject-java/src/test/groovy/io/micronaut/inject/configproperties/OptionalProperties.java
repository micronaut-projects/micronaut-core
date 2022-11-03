package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ConfigurationProperties("config.optional")
public class OptionalProperties {

    private String str;
    private Integer itgr;
    private Double dbl;
    private Long lng;

    public Optional<String> getStr() {
        return Optional.ofNullable(str);
    }

    public void setStr(String str) {
        this.str = str;
    }

    public OptionalInt getItgr() {
        return itgr == null ? OptionalInt.empty() : OptionalInt.of(itgr);
    }

    public void setItgr(Integer itgr) {
        this.itgr = itgr;
    }

    public OptionalDouble getDbl() {
        return dbl == null ? OptionalDouble.empty() : OptionalDouble.of(dbl);
    }

    public void setDbl(Double dbl) {
        this.dbl = dbl;
    }

    public OptionalLong getLng() {
        return lng == null ? OptionalLong.empty() : OptionalLong.of(lng);
    }

    public void setLng(Long lng) {
        this.lng = lng;
    }
}
