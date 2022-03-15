package io.micronaut.scheduling.beanproperties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;

import java.time.Duration;

@ConfigurationProperties("scheduling")
@Requires(property = "spec.name", value = "ScheduledBeanPropertiesSpec")
public class TestConfig
{
    private String stringFixedRate;
    private Duration durationFixedRate;

    public String getStringFixedRate()
    {
        return stringFixedRate;
    }

    public void setStringFixedRate(String stringFixedRate)
    {
        this.stringFixedRate = stringFixedRate;
    }

    public Duration getDurationFixedRate()
    {
        return durationFixedRate;
    }

    public void setDurationFixedRate(Duration durationFixedRate)
    {
        this.durationFixedRate = durationFixedRate;
    }
}
