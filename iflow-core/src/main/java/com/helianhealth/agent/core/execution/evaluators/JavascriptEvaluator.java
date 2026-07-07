package com.helianhealth.agent.core.execution.evaluators;

import com.helianhealth.agent.core.execution.script.GraalScriptUtils;
import com.helianhealth.agent.core.execution.script.ScriptEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * JavaScript 求值器（{@code javascript} 为 {@link GraalJSEvaluator} 的别名）。
 */
@Component(JavascriptEvaluator.NAME)
@Slf4j
public class JavascriptEvaluator implements Evaluator {

    public static final String NAME = "javascript";

    @Override
    public Object evaluate(String expression, Map<String, Object> input) {
        log.debug("Javascript evaluator -- expression: {}", expression);
        String script = GraalScriptUtils.normalizeScript(expression);
        Object inputCopy = ScriptEvaluator.deepCopy(input);
        Object result = ScriptEvaluator.eval(script, inputCopy);
        log.debug("Javascript evaluator -- result: {}", result);
        return result;
    }
}
