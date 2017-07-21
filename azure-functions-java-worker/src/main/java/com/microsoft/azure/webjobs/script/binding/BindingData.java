package com.microsoft.azure.webjobs.script.binding;

public abstract class BindingData {
    protected BindingData(String name, Value value) {
        this.name = name;
        this.value = value;
    }

    public String getName() { return this.name; }
    public Value getValue() { return this.value; }
    protected void setValue(Value value) { this.value = value; }

    private String name;
    private Value value;

    /**
     * The wrapper of the object value so that the 'null' could be put in an Optional&lt;T&gt;.
     */
    public static class Value {
        public Value(Object actual) { this.actual = actual; }
        public Object getActual() { return this.actual; }
        private Object actual;
    }
}
