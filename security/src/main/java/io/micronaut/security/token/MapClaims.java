package io.micronaut.security.token;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A {@link Claims} implementation backed by a {@link Map}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public class MapClaims implements Claims {

    private final Map<String, Object> map;

    public MapClaims(Map<String, Object> m) {
        this.map = m;
    }

    @Nullable
    @Override
    public Object get(String claimName) {
        return map.get(claimName);
    }

    @Nonnull
    @Override
    public Set<String> names() {
        return Collections.unmodifiableSet(map.keySet());
    }

    @Override
    public boolean contains(String claimName) {
        return map.containsKey(claimName);
    }
}
