package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.microsoft.azure.serverless.functions.*;

/**
 * m(String)
 * m(String, ExecutionContext)
 * m(String, String)
 * m(POJO)
 * m(POJO, String)
 * m(POJO, String, String)
 * m(POJO, ExecutionContext)
 * m(POJO, String, ExecutionContext)
 * m(POJO, String, String, ExecutionContext)
 * m(HttpRequestMessage)
 * m(HttpRequestMessage, ExecutionContext)
 * m(HttpRequestMessage, String, ExecutionContext)
 * m(HttpRequestMessage, String, String, ExecutionContext)
 */
public class OverloadResolver {
    public OverloadResolver(ExecutionContext context, JavaMethodInput... inputs) {
        this.inputs = new ArrayList<>(Arrays.asList(inputs));
        this.inputs.add(new JavaMethodInput(context));
    }

    public Optional<Result> resolve(List<Method> methods) {
        Map<Integer, List<Result>> rankSortedResults = methods.stream()
                .map(m -> new Result(m, this.resolve(m.getParameterTypes()).orElse(null)))
                .filter(r -> r.arguments != null).collect(Collectors.groupingBy(
                        r -> Arrays.stream(r.arguments).map(Argument::getRank).max(Integer::compare).orElse(0)));

        for (int rank = 2; rank >= 0; rank--) {
            List<Result> matchedResults = rankSortedResults.getOrDefault(rank, null);
            if (matchedResults != null && matchedResults.size() == 1) {
                matchedResults.get(0).convertValues();
                return Optional.of(matchedResults.get(0));
            }
        }
        return Optional.empty();
    }

    private Optional<Argument[]> resolve(Class<?>[] parameters) {
        if (parameters.length == 0) { return Optional.of(new Argument[0]); }
        List<Optional<Argument>> arguments = Arrays.stream(parameters).map(this::lookforInput).collect(Collectors.toList());
        return arguments.stream().allMatch(Optional::isPresent)
                ? Optional.of(arguments.stream().map(Optional::get).toArray(Argument[]::new))
                : Optional.empty();
    }

    private Optional<Argument> lookforInput(Class<?> parameterType) {
        Optional<Argument> value = this.single(this.inputs, in -> TypeResolver.tryAssign(parameterType, in.getValue())).map(AssignedArgument::new);
        if (value.isPresent()) { return value; }
        return this.single(this.inputs, in -> TypeResolver.tryConvert(parameterType, in.getValue())).map(ConvertedArgument::new);
    }

    private <TSource, TResult> Optional<TResult> single(Iterable<TSource> source, Function<TSource, Optional<TResult>> convert) {
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

    public class Result {
        private Result(Method method, Argument[] arguments) {
            this.method = method;
            this.arguments = arguments;
        }

        public Method getMethod() { return this.method; }
        public Object[] getArguments() { return this.argumentValues; }
        private void convertValues() { this.argumentValues = Arrays.stream(this.arguments).map(Argument::getValue).toArray(); }

        private Method method;
        private Object[] argumentValues;
        private Argument[] arguments;
    }

    private abstract class Argument {
        Argument(Object value, int rank) {
            this.rank = rank;
            this.value = value;
        }

        int getRank() { return this.rank; }
        Object getValue() { return this.value; }

        private int rank;
        private Object value;
    }

    private class ConvertedArgument extends Argument {
        ConvertedArgument(Object value) { super(value, 1); }
    }

    private class AssignedArgument extends Argument {
        AssignedArgument(Object value) { super(value, 2); }
    }

    private List<JavaMethodInput> inputs;
}
