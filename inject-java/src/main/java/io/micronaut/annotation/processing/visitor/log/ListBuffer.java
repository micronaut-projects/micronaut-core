package io.micronaut.annotation.processing.visitor.log;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

class ListBuffer<A> extends AbstractQueue<A> {

    public static <T> ListBuffer<T> of(T x) {
        ListBuffer<T> lb = new ListBuffer<>();
        lb.add(x);
        return lb;
    }

    /**
     * The list of elements of this buffer.
     */
    private AnnList<A> elems;

    /**
     * A pointer pointing to the last element of 'elems' containing data,
     * or null if the list is empty.
     */
    private AnnList<A> last;

    /**
     * The number of element in this buffer.
     */
    private int count;

    /**
     * Has a list been created from this buffer yet?
     */
    private boolean shared;

    /**
     * Create a new initially empty list buffer.
     */
    ListBuffer() {
        clear();
    }

    @Override
    public final void clear() {
        elems = AnnList.nil();
        last = null;
        count = 0;
        shared = false;
    }

    /**
     * Return the number of elements in this buffer.
     */
    public int length() {
        return count;
    }

    @Override
    public int size() {
        return count;
    }

    /**
     * Is buffer empty?
     */
    @Override
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * Is buffer not empty?
     */
    public boolean nonEmpty() {
        return count != 0;
    }

    /**
     * Copy list and sets last.
     */
    private void copy() {
        if (elems.nonEmpty()) {
            AnnList<A> orig = elems;

            elems = last = AnnList.of(orig.head);

            while ((orig = orig.tail).nonEmpty()) {
                last.tail = AnnList.of(orig.head);
                last = last.tail;
            }
        }
    }

    /**
     * Prepend an element to buffer.
     */
    public ListBuffer<A> prepend(A x) {
        elems = elems.prepend(x);
        if (last == null) {
            last = elems;
        }
        count++;
        return this;
    }

    /**
     * Append an element to buffer.
     */
    public ListBuffer<A> append(A x) {
        if (x == null) {
            throw new AssertionError();
        }
        if (shared) {
            copy();
        }
        AnnList<A> newLast = AnnList.of(x);
        if (last != null) {
            last.tail = newLast;
            last = newLast;
        } else {
            elems = last = newLast;
        }
        count++;
        return this;
    }

    /**
     * Append all elements in a list to buffer.
     */
    public ListBuffer<A> appendList(AnnList<A> xs) {
        while (xs.nonEmpty()) {
            append(xs.head);
            xs = xs.tail;
        }
        return this;
    }

    /**
     * Append all elements in a list to buffer.
     */
    public ListBuffer<A> appendList(ListBuffer<A> xs) {
        return appendList(xs.toList());
    }

    /**
     * Append all elements in an array to buffer.
     */
    public ListBuffer<A> appendArray(A[] xs) {
        for (A x : xs) {
            append(x);
        }
        return this;
    }

    /**
     * Convert buffer to a list of all its elements.
     */
    public AnnList<A> toList() {
        shared = true;
        return elems;
    }

    /**
     * Does the list contain the specified element?
     */
    @Override
    public boolean contains(Object x) {
        return elems.contains(x);
    }

    /**
     * Convert buffer to an array
     */
    @Override
    public <T> T[] toArray(T[] vec) {
        return elems.toArray(vec);
    }

    @Override
    public Object[] toArray() {
        return toArray(new Object[size()]);
    }

    /**
     * The first element in this buffer.
     */
    public A first() {
        return elems.head;
    }

    /**
     * Return first element in this buffer and remove
     */
    public A next() {
        A x = elems.head;
        if (!elems.isEmpty()) {
            elems = elems.tail;
            if (elems.isEmpty()) {
                last = null;
            }
            count--;
        }
        return x;
    }

    /**
     * An enumeration of all elements in this buffer.
     */
    @Override
    public Iterator<A> iterator() {
        return new Iterator<>() {
            AnnList<A> elems = ListBuffer.this.elems;

            @Override
            public boolean hasNext() {
                return !elems.isEmpty();
            }

            @Override
            public A next() {
                if (elems.isEmpty()) {
                    throw new NoSuchElementException();
                }
                A elem = elems.head;
                elems = elems.tail;
                return elem;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public boolean add(A a) {
        append(a);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object x : c) {
            if (!contains(x)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends A> c) {
        for (A a : c) {
            append(a);
        }
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(A a) {
        append(a);
        return true;
    }

    @Override
    public A poll() {
        return next();
    }

    @Override
    public A peek() {
        return first();
    }

    public A last() {
        return last != null ? last.head : null;
    }
}
