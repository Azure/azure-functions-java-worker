package com.microsoft.serverless;

import java.util.Arrays;
import com.microsoft.azure.serverless.functions.HttpRequestMessage;
import com.microsoft.azure.serverless.functions.HttpResponseMessage;

public class Calculator {
    public static String echo(String input) {
        return "Hello, " + input + "!";
    }

    public static int negate(int n) {
        return -n;
    }

    public static int add(int[] numbers) {
        return Arrays.stream(numbers).sum();
    }

    public static ComplexNumber multiply(TwoComplexNumbers operands) {
        ComplexNumber n1 = operands.getN1(),
                      n2 = operands.getN2();
        return new ComplexNumber(n1.getR() * n2.getR() - n1.getI() * n2.getI(), n1.getR() * n2.getI() + n1.getI() + n2.getR());
    }

    public static String echo(HttpRequestMessage request) {
        String source = request.getQueryParameters().getOrDefault("name", null);
        if (source == null || source.isEmpty()) {
            source = request.getBody();
        }
        return echo(source);
    }

    public static HttpResponseMessage upload(byte[] data) {
        return new HttpResponseMessage(202, data.length + " bytes");
    }

    public static class ComplexNumber {
        public ComplexNumber(int r, int i) {
            this.r = r;
            this.i = i;
        }
        int getR() { return this.r; }
        int getI() { return this.i; }
        private int r;
        private int i;
    }

    public static class TwoComplexNumbers {
        ComplexNumber getN1() { return this.n1; }
        ComplexNumber getN2() { return this.n2; }
        private ComplexNumber n1, n2;
    }
}
