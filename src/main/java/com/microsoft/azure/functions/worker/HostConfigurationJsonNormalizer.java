package com.microsoft.azure.functions.worker;

import java.io.ByteArrayOutputStream;

/**
 * Validates JSON and copies raw tokens, removing comments, trailing commas and whitespace.
 * The caller supplies already bounded, strictly decoded UTF-8. A DOM or numeric conversion
 * would lose duplicate properties, escape spellings or arbitrarily long number lexemes.
 */
final class HostConfigurationJsonNormalizer {
    private final byte[] source;
    private final ByteArrayOutputStream output;
    private int position;

    private HostConfigurationJsonNormalizer(byte[] source) {
        this.source = source;
        this.output = new ByteArrayOutputStream(source.length);
    }

    static byte[] normalize(byte[] utf8) throws InvalidJsonException {
        HostConfigurationJsonNormalizer normalizer = new HostConfigurationJsonNormalizer(utf8);
        normalizer.value(0);
        normalizer.skipTrivia();
        if (normalizer.position != utf8.length) {
            throw new InvalidJsonException();
        }
        return normalizer.output.toByteArray();
    }

    private void value(int depth) throws InvalidJsonException {
        skipTrivia();
        int next = peek();
        switch (next) {
            case '{':
            case '[':
                container(depth + 1, next == '{');
                return;
            case '"':
                string();
                return;
            case 't':
                literal("true");
                return;
            case 'f':
                literal("false");
                return;
            case 'n':
                literal("null");
                return;
            default:
                if (next == '-' || isDigit(next)) {
                    number();
                    return;
                }
                throw new InvalidJsonException();
        }
    }

    private void container(int depth, boolean object) throws InvalidJsonException {
        if (depth > 64) {
            throw new InvalidJsonException();
        }
        output.write(source[position++]);
        int end = object ? '}' : ']';
        skipTrivia();
        if (consume(end)) {
            output.write(end);
            return;
        }
        while (true) {
            if (object) {
                string();
                skipTrivia();
                require(':');
                output.write(':');
            }
            value(depth);
            skipTrivia();
            if (consume(end)) {
                break;
            }
            require(',');
            skipTrivia();
            if (consume(end)) {
                break;
            }
            output.write(',');
        }
        output.write(end);
    }

    private void string() throws InvalidJsonException {
        int start = position;
        require('"');
        while (position < source.length) {
            int current = source[position++] & 0xFF;
            if (current == '"') {
                output.write(source, start, position - start);
                return;
            }
            if (current < 0x20) {
                throw new InvalidJsonException();
            }
            if (current == '\\') {
                int escape = peek();
                position++;
                if (escape == 'u') {
                    for (int i = 0; i < 4; i++) {
                        int digit = peek();
                        if (!isDigit(digit) && !(digit >= 'a' && digit <= 'f') && !(digit >= 'A' && digit <= 'F')) {
                            throw new InvalidJsonException();
                        }
                        position++;
                    }
                } else if (escape != '"' && escape != '\\' && escape != '/'
                        && escape != 'b' && escape != 'f' && escape != 'n' && escape != 'r' && escape != 't') {
                    throw new InvalidJsonException();
                }
            }
        }
        throw new InvalidJsonException();
    }

    private void number() throws InvalidJsonException {
        int start = position;
        consume('-');
        if (!consume('0')) {
            if (peek() < '1' || peek() > '9') {
                throw new InvalidJsonException();
            }
            digits();
        }
        if (consume('.')) {
            digits();
        }
        if (consume('e') || consume('E')) {
            if (!consume('+')) {
                consume('-');
            }
            digits();
        }
        output.write(source, start, position - start);
    }

    private void digits() throws InvalidJsonException {
        if (!isDigit(peek())) {
            throw new InvalidJsonException();
        }
        do {
            position++;
        } while (isDigit(peek()));
    }

    private void literal(String expected) throws InvalidJsonException {
        int start = position;
        for (int i = 0; i < expected.length(); i++) {
            require(expected.charAt(i));
        }
        output.write(source, start, position - start);
    }

    private void skipTrivia() throws InvalidJsonException {
        while (position < source.length) {
            int next = peek();
            if (next == ' ' || next == '\t' || next == '\n' || next == '\r') {
                position++;
            } else if (next == '/' && position + 1 < source.length && source[position + 1] == '/') {
                position += 2;
                while (position < source.length && peek() != '\r' && peek() != '\n') {
                    // Match Utf8JsonReader: these are neither valid comment text nor line endings.
                    if (position + 2 < source.length && peek() == 0xE2
                            && source[position + 1] == (byte) 0x80
                            && (source[position + 2] == (byte) 0xA8 || source[position + 2] == (byte) 0xA9)) {
                        throw new InvalidJsonException();
                    }
                    position++;
                }
            } else if (next == '/' && position + 1 < source.length && source[position + 1] == '*') {
                position += 2;
                while (position + 1 < source.length && !(source[position] == '*' && source[position + 1] == '/')) {
                    position++;
                }
                if (position + 1 >= source.length) {
                    throw new InvalidJsonException();
                }
                position += 2;
            } else {
                return;
            }
        }
    }

    private int peek() {
        return position < source.length ? source[position] & 0xFF : -1;
    }

    private boolean consume(int expected) {
        if (peek() != expected) {
            return false;
        }
        position++;
        return true;
    }

    private void require(int expected) throws InvalidJsonException {
        if (!consume(expected)) {
            throw new InvalidJsonException();
        }
    }

    private static boolean isDigit(int value) {
        return value >= '0' && value <= '9';
    }

    static final class InvalidJsonException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
