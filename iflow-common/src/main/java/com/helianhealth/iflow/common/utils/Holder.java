package com.helianhealth.iflow.common.utils;

/**
 * Helper Class for hold a value.
 */
public class Holder<T> {

    private volatile T value;

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}
