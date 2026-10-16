package io.micronaut.python.annotation.processing.test.flow;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-memory implementation of the repository.
 */
@Singleton
public class InMemoryBookRepository implements BookRepository {
    private final List<String> titles = new ArrayList<>();
    private final Map<String, Integer> pages = new LinkedHashMap<>();

    @Override
    public synchronized String save(String title) {
        titles.add(title);
        pages.put(title, title.length() * 10);
        return title;
    }

    @Override
    public synchronized long count() {
        return titles.size();
    }

    @Override
    public synchronized List<String> titles() {
        return List.copyOf(titles);
    }

    @Override
    public synchronized Map<String, Integer> pages() {
        return Map.copyOf(pages);
    }
}
