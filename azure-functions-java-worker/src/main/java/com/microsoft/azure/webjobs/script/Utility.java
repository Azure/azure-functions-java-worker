package com.microsoft.azure.webjobs.script;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

public class Utility {
    @SuppressWarnings("unchecked")
    public static <TSource, TResult> TResult[] map(TSource[] source, Function<TSource, TResult> mapper) {
        final TResult[] result = (TResult[]) Array.newInstance(Object.class, source.length);
        for (int i = 0; i < source.length; i++) {
            result[i] = mapper.apply(source[i]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(List<T> list) {
        final T[] result = (T[]) Array.newInstance(Object.class, list.size());
        return list.toArray(result);
    }

    public static <T> void forEach(T[] source, Consumer<T> consumer) {
        for (T item : source) {
            consumer.accept(item);
        }
    }

    public static <TSource, TResult> List<TResult> map(Iterable<TSource> source, Function<TSource, Optional<TResult>> mapper) {
        final List<TResult> result = new ArrayList<>();
        for (TSource item : source) {
            mapper.apply(item).ifPresent(result::add);
        }
        return result;
    }

    public static <TSource, TResult> Optional<TResult> single(Iterable<TSource> source, Function<TSource, Optional<TResult>> convert) {
        int count = 0;
        Optional<TResult> result = Optional.empty();
        for (TSource item : source) {
            Optional<TResult> convertedItem = convert.apply(item);
            if (convertedItem.isPresent()) {
                result = convertedItem;
                if (++count >= 2) { return Optional.empty(); }
            }
        }
        return result;
    }

    public static <T> Optional<T> singleMax(Iterable<T> source, Comparator<? super T> comparator) {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) { return Optional.empty(); }
        boolean dupMax = false;
        T max = it.next();
        while (it.hasNext()) {
            T item = it.next();
            int compResult = comparator.compare(item, max);
            if (compResult > 0) {
                max = item;
                dupMax = false;
            } else if (compResult == 0) {
                dupMax = true;
            }
        }
        return dupMax ? Optional.empty() : Optional.of(max);
    }
}
