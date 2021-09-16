package io.micronaut.inject.requires.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("outer")
public class TestConfig
{
    private boolean enabled;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    @ConfigurationProperties("inner")
    public static class InnerConfig
    {
        private boolean enabled;

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }
    }
}
