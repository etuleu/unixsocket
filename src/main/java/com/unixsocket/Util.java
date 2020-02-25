package com.unixsocket;

public class Util {

    public static void print(String s) {
        System.out.println(s);
    }

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {

        }
    }
}
