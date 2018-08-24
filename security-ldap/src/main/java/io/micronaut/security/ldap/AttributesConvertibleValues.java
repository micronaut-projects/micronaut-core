package io.micronaut.security.ldap;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.*;

public class AttributesConvertibleValues implements ConvertibleValues<Object> {

    private final Attributes attributes;
    private final ConversionService conversionService = ConversionService.SHARED;

    public AttributesConvertibleValues(Attributes attributes) {
        this.attributes = attributes;
    }

    @Override
    public Set<String> names() {
        Set<String> names = new HashSet<>();
        NamingEnumeration<String> ids = attributes.getIDs();
        try {
            while (ids.hasMore()) {
                names.add(ids.next());
            }
        } catch (NamingException e) {
            //swallow
        }
        return names;
    }

    @Override
    public Collection<Object> values() {
        List<Object> values = new ArrayList<>(attributes.size());
        NamingEnumeration<? extends Attribute> all = attributes.getAll();
        try {
            while (all.hasMore()) {
                values.add(all.next().get());
            }
        } catch (NamingException e) {
            //swallow
        }
        return values;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        try {
            NamingEnumeration<?> enumeration = attributes.get(name.toString()).getAll();
            List<Object> results = new ArrayList<>();
            while (enumeration.hasMore()) {
                results.add(enumeration.next());
            }
            if (results.size() > 0) {
                Object value;
                if (results.size() > 1) {
                    value = results;
                } else {
                    value = results.get(0);
                }
                return conversionService.convert(value, conversionContext);
            } else {
                return Optional.empty();
            }
        } catch (NamingException e) {
            return Optional.empty();
        }
    }
}
