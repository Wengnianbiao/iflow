package com.helianhealth.iflow.core.execution.evaluators;

import com.helianhealth.iflow.common.domain.run.FileAccess;
import com.helianhealth.iflow.core.execution.script.GraalScriptUtils;
import com.helianhealth.iflow.core.execution.script.ScriptEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GraalJS 求值器。
 */
@Component(GraalJSEvaluator.NAME)
public class GraalJSEvaluator implements Evaluator {

    private static final Logger log = LoggerFactory.getLogger(GraalJSEvaluator.class);

    public static final String NAME = "graaljs";

    @Override
    public Object evaluate(String expression, Map<String, Object> input) {
        return evaluateWithBindings(expression, input, Map.of());
    }

    public Object evaluateWithBindings(String expression, Object input, Map<String, Object> bindings) {
        return evaluateWithBindings(expression, input, bindings, null);
    }

    public Object evaluateWithFileAccess(String expression, Object input, FileAccess fileAccess) {
        return evaluateWithBindings(expression, input, Map.of(), fileAccess);
    }

    private Object evaluateWithBindings(
            String expression, Object input, Map<String, Object> bindings, FileAccess fileAccess) {
        log.debug("GraalJS evaluator -- expression: {}", expression);
        String script = GraalScriptUtils.normalizeScript(expression);
        Object inputCopy = ScriptEvaluator.deepCopy(input);
        Object result = ScriptEvaluator.eval(script, inputCopy,
                (Map<String, Object>) ScriptEvaluator.deepCopy(bindings), fileAccess);
        log.debug("GraalJS evaluator -- result: {}", result);
        return result;
    }
}
