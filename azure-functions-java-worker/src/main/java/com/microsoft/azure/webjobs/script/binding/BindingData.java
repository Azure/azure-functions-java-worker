package com.microsoft.azure.webjobs.script.binding;

public abstract class BindingData<T> {
    BindingData(String name, Value<T> value) {
        this.name = name;
        this.value = value;
    }

    public String getName() { return this.name; }
    public Value<T> getValue() { return this.value; }
    public T getActualValue() { return this.value.getActual(); }
    protected void setValue(Value<T> value) { this.value = value; }

    private String name;
    private Value<T> value;

    /**
     * The wrapper of the object value so that the 'null' could be put in an Optional&lt;T&gt;.
     */
    public static class Value<T> {
        Value(T actual) { this.actual = actual; }
        public T getActual() { return this.actual; }
        private T actual;
    }
}
