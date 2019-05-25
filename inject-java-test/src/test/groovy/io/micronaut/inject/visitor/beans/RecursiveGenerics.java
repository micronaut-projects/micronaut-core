package io.micronaut.inject.visitor.beans;

import java.util.ArrayList;
import java.util.List;

public abstract class RecursiveGenerics<T extends RecursiveGenerics<T>> {

    private String name;
    private List<T> revisions = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public List<T> getRevisions() {
        return revisions;
    }

    public void setRevisions(List<T> revisions) {
        this.revisions = revisions;
    }
}

