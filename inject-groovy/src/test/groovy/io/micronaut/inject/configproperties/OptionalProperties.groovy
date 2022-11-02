package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("config.optional")
class OptionalProperties {

    private String str
    private Integer itgr
    private Double dbl
    private Long lng

    Optional<String> getStr() {
        return Optional.ofNullable(str)
    }

    void setStr(String str) {
        this.str = str
    }

    OptionalInt getItgr() {
        return itgr == null ? OptionalInt.empty() : OptionalInt.of(itgr)
    }

    void setItgr(Integer itgr) {
        this.itgr = itgr
    }

    OptionalDouble getDbl() {
        return dbl == null ? OptionalDouble.empty() : OptionalDouble.of(dbl)
    }

    void setDbl(Double dbl) {
        this.dbl = dbl
    }

    OptionalLong getLng() {
        return lng == null ? OptionalLong.empty() : OptionalLong.of(lng)
    }

    void setLng(Long lng) {
        this.lng = lng
    }
}
