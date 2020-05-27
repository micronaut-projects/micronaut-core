package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("foo.bar")
public class MyConfigInner {

    private List<InnerVal> innerVals;

    public List<InnerVal> getInnerVals() {
        return innerVals;
    }

    public void setInnerVals(List<InnerVal> innerVals) {
        this.innerVals = innerVals;
    }

    public static class InnerVal {

        private Integer expireUnsignedSeconds;

        public Integer getExpireUnsignedSeconds() {
            return expireUnsignedSeconds;
        }

        public void setExpireUnsignedSeconds(Integer expireUnsignedSeconds) {
            this.expireUnsignedSeconds = expireUnsignedSeconds;
        }
    }

}
