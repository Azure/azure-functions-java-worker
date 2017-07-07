package com.microsoft.serverless;

import java.util.Arrays;

public class Calculator {
    public static int negate(int n) {
        return -n;
    }

    public static int add(int[] numbers) {
        return Arrays.stream(numbers).sum();
    }

    public static ComplexNumber multiply(TwoComplexNumbers operands) {
        ComplexNumber n1 = operands.getN1(),
                      n2 = operands.getN2();
        return new ComplexNumber(
                n1.getRe() * n2.getRe() - n1.getIm() * n2.getIm(),
                n1.getRe() * n2.getIm() + n1.getIm() * n2.getRe());
    }

    public static class ComplexNumber {
        public ComplexNumber(int re, int im) {
            this.r = re;
            this.i = im;
        }
        int getRe() { return this.r; }
        int getIm() { return this.i; }
        private int r;
        private int i;
    }

    public static class TwoComplexNumbers {
        ComplexNumber getN1() { return this.n1; }
        ComplexNumber getN2() { return this.n2; }
        private ComplexNumber n1, n2;
    }
}
