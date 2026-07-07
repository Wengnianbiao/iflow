package com.helianhealth.agent.common.extension.fixture;

public class CustomTestExtension implements TestExtension {
    @Override
    public String echo(String input) {
        return "custom:" + input;
    }
}
