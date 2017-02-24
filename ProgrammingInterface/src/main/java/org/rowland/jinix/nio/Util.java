package org.rowland.jinix.nio;

import java.nio.charset.Charset;
import java.security.AccessController;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by rsmith on 1/7/2017.
 */
public class Util {
    private Util() { }

    private static final Charset jnuEncoding = Charset.forName("UTF-8");

    /**
     * Returns {@code Charset} corresponding to the sun.jnu.encoding property
     */
    static Charset jnuEncoding() {
        return jnuEncoding;
    }

    /**
     * Encodes the given String into a sequence of bytes using the {@code Charset}
     * specified by the sun.jnu.encoding property.
     */
    static byte[] toBytes(String s) {
        return s.getBytes(jnuEncoding);
    }

    /**
     * Constructs a new String by decoding the specified array of bytes using the
     * {@code Charset} specified by the sun.jnu.encoding property.
     */
    static String toString(byte[] bytes) {
        return new String(bytes, jnuEncoding);
    }


    /**
     * Splits a string around the given character. The array returned by this
     * method contains each substring that is terminated by the character. Use
     * for simple string spilting cases when needing to avoid loading regex.
     */
    static String[] split(String s, char c) {
        int count = 0;
        for (int i=0; i<s.length(); i++) {
            if (s.charAt(i) == c)
                count++;
        }
        String[] result = new String[count+1];
        int n = 0;
        int last = 0;
        for (int i=0; i<s.length(); i++) {
            if (s.charAt(i) == c) {
                result[n++] = s.substring(last, i);
                last = i + 1;
            }
        }
        result[n] = s.substring(last, s.length());
        return result;
    }

    /**
     * Returns a Set containing the given elements.
     */
    @SafeVarargs
    static <E> Set<E> newSet(E... elements) {
        HashSet<E> set = new HashSet<>();
        for (E e: elements) {
            set.add(e);
        }
        return set;
    }

    /**
     * Returns a Set containing all the elements of the given Set plus
     * the given elements.
     */
    @SafeVarargs
    static <E> Set<E> newSet(Set<E> other, E... elements) {
        HashSet<E> set = new HashSet<>(other);
        for (E e: elements) {
            set.add(e);
        }
        return set;
    }

}
