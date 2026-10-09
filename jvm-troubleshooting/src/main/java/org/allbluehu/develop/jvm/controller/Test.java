package org.allbluehu.develop.jvm.controller;

/**
 * @author huaolan created on 2026/07/31
 *
 */
public class Test {
    int sum;

    public static int getSum() {

        return 0;
    }

    public static void main(String[] args) {
        Object obj = "Hello";
        String result = switch (obj) {
            case Integer i -> "Integer: " + i;
            case String s when s.length() > 10 -> "Long String";
            case String s -> "String: " + s;
            case null -> "null";
            default -> "Other";
        };
        System.out.println(result);
    }
}
