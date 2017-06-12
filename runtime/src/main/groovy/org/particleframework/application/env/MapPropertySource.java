package org.particleframework.application.env;

import java.util.Iterator;
import java.util.Map;

/**
 * A {@link PropertySource} that uses a map
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MapPropertySource implements PropertySource {
    private final Map map;

    public MapPropertySource(Map map) {
        this.map = map;
    }

    @Override
    public Object get(String key) {
        return map.get(key);
    }

    @Override
    public Iterator<String> iterator() {
        Iterator i = map.keySet().iterator();

        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public String next() {
                return i.next().toString();
            }
        };
    }
}
