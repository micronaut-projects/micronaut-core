package io.micronaut.reflection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A type whose property is read as a List and written as a Collection: the processor describes a read
 * property and a write property of its own, each carrying the argument of the accessor behind it, where the
 * one property merged from both carries the type of the setter alone.
 */
public class SupTags {

    private List<String> tags = new ArrayList<>();

    public List<String> getTags() {
        return tags;
    }

    public void setTags(Collection<String> tags) {
        this.tags = new ArrayList<>(tags);
    }
}
