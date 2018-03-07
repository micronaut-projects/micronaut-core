package io.micronaut.context.env;

import java.util.Iterator;
import java.util.Map;

/**
 * A {@link PropertySource} that uses a map
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MapPropertySource implements PropertySource {
    private final String name;
    private final Map map;

    public MapPropertySource(String name, Map map) {
        this.name = name;
        this.map = map;
    }

    @Override
    public String getName() {
        return name;
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

    /**
     * Create a new {@link MapPropertySource} from the given map
     *
     * @param map The map
     * @return The map property source
     */
    public static MapPropertySource of(String name, Map<String, Object> map) {
        return new MapPropertySource(name, map);
    }


    @Override
    public String toString() {
        return getName();
    }
}
