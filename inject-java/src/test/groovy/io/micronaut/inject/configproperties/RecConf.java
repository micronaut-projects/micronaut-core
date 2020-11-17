package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@ConfigurationProperties("rec")
public class RecConf {
    private List<String> namesListOf;
    private Map<String, RecConf> mapChildren;
    private List<RecConf> listChildren;

    public List<String> getNamesListOf() {
        return namesListOf;
    }

    public void setNamesListOf(List<String> namesListOf) {
        this.namesListOf = namesListOf;
    }

    public List<RecConf> getListChildren() {
        return listChildren;
    }

    public void setListChildren(List<RecConf> listChildren) {
        this.listChildren = listChildren;
    }

    public Map<String, RecConf> getMapChildren() {
        return mapChildren;
    }

    public void setMapChildren(Map<String, RecConf> mapChildren) {
        this.mapChildren = mapChildren;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecConf recConf = (RecConf) o;
        return Objects.equals(namesListOf, recConf.namesListOf) &&
                Objects.equals(mapChildren, recConf.mapChildren) &&
                Objects.equals(listChildren, recConf.listChildren);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namesListOf, mapChildren, listChildren);
    }
}