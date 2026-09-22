package com.helianhealth.iflow.common.extension.fixture;

public class CustomTestExtension implements TestExtension {
    @Override
    public String echo(String input) {
        return "custom:" + input;
    }
}
