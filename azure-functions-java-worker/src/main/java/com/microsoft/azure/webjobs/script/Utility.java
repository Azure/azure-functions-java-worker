package com.microsoft.azure.webjobs.script;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

public class Utility {
    @SuppressWarnings("unchecked")
    public static <TSource, TResult> TResult[] map(TSource[] source, Class<TResult> resultType, Function<TSource, TResult> mapper) {
        final TResult[] result = (TResult[]) Array.newInstance(resultType, source.length);
        for (int i = 0; i < source.length; i++) { result[i] = mapper.apply(source[i]); }
        return result;
    }

    public static <TSource, TResult> List<TResult> map(Iterable<TSource> source, Function<TSource, TResult> mapper) {
        final List<TResult> result = new ArrayList<>();
        for (TSource item : source) { result.add(mapper.apply(item)); }
        return result;
    }

    public static <T> void forEach(T[] source, Consumer<T> consumer) {
        for (T item : source) { consumer.accept(item); }
    }

    public static <TSource, TResult> List<TResult> mapOptional(Iterable<TSource> source, Function<TSource, Optional<TResult>> mapper) {
        final List<TResult> result = new ArrayList<>();
        for (TSource item : source) { mapper.apply(item).ifPresent(result::add); }
        return result;
    }

    public static <TSource, TResult> List<TResult> take(Iterable<TSource> source, int max, Function<TSource, Optional<TResult>> convert) {
        List<TResult> result = new ArrayList<>();
        for (TSource item : source) {
            Optional<TResult> convertedItem = convert.apply(item);
            if (convertedItem.isPresent()) {
                result.add(convertedItem.get());
                if (result.size() >= max) { break; }
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

    public static String stackTraceToString(Throwable t) {
        if (t == null) { return null; }
        try (StringWriter writer = new StringWriter();
             PrintWriter printer = new PrintWriter(writer)) {
            t.printStackTrace(printer);
            return writer.toString();
        } catch (IOException nestedException) {
            nestedException.printStackTrace();
            return t.toString();
        }
    }
}
