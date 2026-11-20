package io.micronaut.python.annotation.processing.test.flow;

import java.util.List;
import java.util.Map;

/**
 * A repository as Micronaut Data declares one: a Java interface the Python beans call.
 */
public interface BookRepository {

    String save(String title);

    long count();

    List<String> titles();

    Map<String, Integer> pages();
}
