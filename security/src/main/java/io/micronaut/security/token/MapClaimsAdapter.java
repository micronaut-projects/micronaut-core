package io.micronaut.security.token;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * Adapts from {@link Map} to {@link Claims}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public class MapClaimsAdapter implements Claims {
    private final Map<String, Object> m;

    public MapClaimsAdapter(Map<String, Object> m) {
        this.m = m;
    }

    @Nullable
    @Override
    public Object get(String claimName) {
        return m.get(claimName);
    }

    @Nonnull
    @Override
    public Set<String> names() {
        return m.keySet();
    }

    @Override
    public boolean contains(String claimName) {
        return m.containsKey(claimName);
    }
}
