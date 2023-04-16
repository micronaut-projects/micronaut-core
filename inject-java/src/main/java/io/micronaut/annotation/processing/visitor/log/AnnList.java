package io.micronaut.annotation.processing.visitor.log;

import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

class AnnList<A> extends AbstractCollection<A> implements List<A> {

    private static final AnnList<?> EMPTY_LIST = new AnnList<>(null, null) {
        @Override
        public AnnList<Object> setTail(AnnList<Object> tail) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    };

    /**
     * The first element of the list, supposed to be immutable.
     */
    public A head;

    /**
     * The remainder of the list except for its first element, supposed
     * to be immutable.
     */
    //@Deprecated
    public AnnList<A> tail;

    /**
     * Construct a list given its head and tail.
     */
    AnnList(A head, AnnList<A> tail) {
        this.tail = tail;
        this.head = head;
    }

    /**
     * Construct an empty list.
     */
    @SuppressWarnings("unchecked")
    public static <A> AnnList<A> nil() {
        return (AnnList<A>) EMPTY_LIST;
    }

    /**
     * Construct a list consisting of given element.
     */
    public static <A> AnnList<A> of(A x1) {
        return new AnnList<>(x1, AnnList.nil());
    }

    /**
     * Does list have no elements?
     */
    @Override
    public boolean isEmpty() {
        return tail == null;
    }

    /**
     * Does list have elements?
     */
    public boolean nonEmpty() {
        return tail != null;
    }

    @Override
    public int size() {
        AnnList<A> l = this;
        int len = 0;
        while (l.tail != null) {
            l = l.tail;
            len++;
        }
        return len;
    }

    public AnnList<A> setTail(AnnList<A> tail) {
        this.tail = tail;
        return tail;
    }

    /**
     * Prepend given element to front of list, forming and returning
     * a new list.
     */
    public AnnList<A> prepend(A x) {
        return new AnnList<>(x, this);
    }

    /**
     * Prepend given list of elements to front of list, forming and returning
     * a new list.
     */
    public AnnList<A> prependList(AnnList<A> xs) {
        if (isEmpty()) {
            return xs;
        }
        if (xs.isEmpty()) {
            return this;
        }
        if (xs.tail.isEmpty()) {
            return prepend(xs.head);
        }
        // return this.prependList(xs.tail).prepend(xs.head);
        AnnList<A> result = this;
        AnnList<A> rev = xs.reverse();

        if (rev == xs) {
            throw new AssertionError();
        }

        // since xs.reverse() returned a new list, we can reuse the
        // individual List objects, instead of allocating new ones.
        while (rev.nonEmpty()) {
            AnnList<A> h = rev;
            rev = rev.tail;
            h.setTail(result);
            result = h;
        }
        return result;
    }

    /**
     * Reverse list.
     * If the list is empty or a singleton, then the same list is returned.
     * Otherwise, a new list is formed.
     */
    public AnnList<A> reverse() {
        // if it is empty or a singleton, return itself
        if (isEmpty() || tail.isEmpty()) {
            return this;
        }

        AnnList<A> rev = nil();
        for (AnnList<A> l = this; l.nonEmpty(); l = l.tail) {
            rev = new AnnList<>(l.head, rev);
        }
        return rev;
    }

    /**
     * Append given element at length, forming and returning
     * a new list.
     */
    public AnnList<A> append(A x) {
        return of(x).prependList(this);
    }

    /**
     * Copy successive elements of this list into given vector until
     * list is exhausted or end of vector is reached.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] vec) {
        int i = 0;
        AnnList<A> l = this;
        Object[] dest = vec;
        while (l.nonEmpty() && i < vec.length) {
            dest[i] = l.head;
            l = l.tail;
            i++;
        }
        if (l.isEmpty()) {
            if (i < vec.length) {
                vec[i] = null;
            }
            return vec;
        }

        vec = (T[]) Array.newInstance(vec.getClass().getComponentType(), size());
        return toArray(vec);
    }

    @Override
    public Object[] toArray() {
        return toArray(new Object[size()]);
    }

    /**
     * Form a string listing all elements with given separator character.
     */
    public String toString(String sep) {
        if (isEmpty()) {
            return "";
        } else {
            StringBuilder buf = new StringBuilder();
            buf.append(head);
            for (AnnList<A> l = tail; l.nonEmpty(); l = l.tail) {
                buf.append(sep);
                buf.append(l.head);
            }
            return buf.toString();
        }
    }

    /**
     * Form a string listing all elements with comma as the separator character.
     */
    @Override
    public String toString() {
        return toString(",");
    }

    /**
     * Compute a hash code, overrides Object
     */
    @Override
    public int hashCode() {
        AnnList<A> l = this;
        int h = 1;
        while (l.tail != null) {
            h = h * 31 + (l.head == null ? 0 : l.head.hashCode());
            l = l.tail;
        }
        return h;
    }

    /**
     * Is this list the same as other list?
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof AnnList<?> javacList) {
            return equals(this, javacList);
        }
        if (other instanceof List<?> javaUtilList) {
            AnnList<A> t = this;
            Iterator<?> oIter = javaUtilList.iterator();
            while (t.tail != null && oIter.hasNext()) {
                Object o = oIter.next();
                if (!Objects.equals(t.head, o)) {
                    return false;
                }
                t = t.tail;
            }
            return (t.isEmpty() && !oIter.hasNext());
        }
        return false;
    }

    /**
     * Are the two lists the same?
     */
    public static boolean equals(AnnList<?> xs, AnnList<?> ys) {
        while (xs.tail != null && ys.tail != null) {
            if (xs.head == null) {
                if (ys.head != null) {
                    return false;
                }
            } else {
                if (!xs.head.equals(ys.head)) {
                    return false;
                }
            }
            xs = xs.tail;
            ys = ys.tail;
        }
        return xs.tail == null && ys.tail == null;
    }

    /**
     * Does the list contain the specified element?
     */
    @Override
    public boolean contains(Object x) {
        AnnList<A> l = this;
        while (l.tail != null) {
            if (x == null) {
                if (l.head == null) {
                    return true;
                }
            } else {
                if (l.head.equals(x)) {
                    return true;
                }
            }
            l = l.tail;
        }
        return false;
    }

    private static final Iterator<?> EMPTYITERATOR = new Iterator<>() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    };

    @SuppressWarnings("unchecked")
    private static <A> Iterator<A> emptyIterator() {
        return (Iterator<A>) EMPTYITERATOR;
    }

    @Override
    public Iterator<A> iterator() {
        if (tail == null) {
            return emptyIterator();
        }
        return new Iterator<>() {
            AnnList<A> elems = AnnList.this;

            @Override
            public boolean hasNext() {
                return elems.tail != null;
            }

            @Override
            public A next() {
                if (elems.tail == null) {
                    throw new NoSuchElementException();
                }
                A result = elems.head;
                elems = elems.tail;
                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public A get(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }

        AnnList<A> l = this;
        for (int i = index; i-- > 0 && !l.isEmpty(); l = l.tail) {
        }

        if (l.isEmpty()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", " + "Size: " + size());
        }
        return l.head;
    }

    @Override
    public boolean addAll(int index, Collection<? extends A> c) {
        if (c.isEmpty()) {
            return false;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public A set(int index, A element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, A element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public A remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        int i = 0;
        for (AnnList<A> l = this; l.tail != null; l = l.tail, i++) {
            if (l.head == null ? o == null : l.head.equals(o)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        int last = -1;
        int i = 0;
        for (AnnList<A> l = this; l.tail != null; l = l.tail, i++) {
            if (l.head == null ? o == null : l.head.equals(o)) {
                last = i;
            }
        }
        return last;
    }

    @Override
    public ListIterator<A> listIterator() {
        return Collections.unmodifiableList(new ArrayList<>(this)).listIterator();
    }

    @Override
    public ListIterator<A> listIterator(int index) {
        return Collections.unmodifiableList(new ArrayList<>(this)).listIterator(index);
    }

    @Override
    public List<A> subList(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size() || fromIndex > toIndex) {
            throw new IllegalArgumentException();
        }

        ArrayList<A> a = new ArrayList<>(toIndex - fromIndex);
        int i = 0;
        for (AnnList<A> l = this; l.tail != null; l = l.tail, i++) {
            if (i == toIndex) {
                break;
            }
            if (i >= fromIndex) {
                a.add(l.head);
            }
        }

        return Collections.unmodifiableList(a);
    }
}
