package com.helianhealth.agent.core.execution.evaluators;

import com.helianhealth.agent.core.execution.script.GraalScriptUtils;
import com.helianhealth.agent.core.execution.script.ScriptEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GraalJS 求值器。
 */
@Component(GraalJSEvaluator.NAME)
@Slf4j
public class GraalJSEvaluator implements Evaluator {

    public static final String NAME = "graaljs";

    @Override
    public Object evaluate(String expression, Map<String, Object> input) {
        log.debug("GraalJS evaluator -- expression: {}", expression);
        String script = GraalScriptUtils.normalizeScript(expression);
        Object inputCopy = ScriptEvaluator.deepCopy(input);
        Object result = ScriptEvaluator.eval(script, inputCopy);
        log.debug("GraalJS evaluator -- result: {}", result);
        return result;
    }
}
