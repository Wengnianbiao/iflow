package com.helianhealth.agent.common.extension.fixture;

public class DefaultTestExtension implements TestExtension {
    @Override
    public String echo(String input) {
        return "default:" + input;
    }
}
