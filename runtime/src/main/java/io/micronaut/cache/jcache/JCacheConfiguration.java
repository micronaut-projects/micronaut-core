package io.micronaut.cache.jcache;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;

@ConfigurationProperties("micronaut.jcache")
public class JCacheConfiguration implements Toggleable {

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;


    /**
     * The default convert value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_CONVERT = true;

    private boolean enabled = DEFAULT_ENABLED;
    private boolean convert = DEFAULT_CONVERT;

    /**
     * @return True if the Java cache manager should be enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable the distributed configuration
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return True if cache values should be converted to the required type. They will
     * be cast to the type otherwise
     */
    public boolean isConvert() {
        return convert;
    }

    /**
     * @param convert Sets whether retrieved cache values should be converted (true), or cast (false)
     *                to the required type. Default value ({@value #DEFAULT_CONVERT})
     */
    public void setConvert(boolean convert) {
        this.convert = convert;
    }
}
