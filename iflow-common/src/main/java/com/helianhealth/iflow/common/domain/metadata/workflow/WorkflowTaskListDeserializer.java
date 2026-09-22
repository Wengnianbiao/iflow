package com.helianhealth.iflow.common.domain.metadata.workflow;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * 兼容历史/错误数据：部分 workflow JSON 将 {@code loopOver} 写成了 boolean（如 {@code false}），
 * 而非 DO_WHILE 所需的子任务数组。
 */
public class WorkflowTaskListDeserializer extends JsonDeserializer<List<WorkflowTask>> {

    @Override
    public List<WorkflowTask> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_FALSE
                || token == JsonToken.VALUE_TRUE
                || token == JsonToken.VALUE_NULL) {
            return new LinkedList<>();
        }
        if (token != JsonToken.START_ARRAY) {
            parser.skipChildren();
            return new LinkedList<>();
        }
        var type = TypeFactory.defaultInstance().constructCollectionType(LinkedList.class, WorkflowTask.class);
        return context.readValue(parser, type);
    }

    @Override
    public List<WorkflowTask> getNullValue(DeserializationContext ctxt) {
        return new LinkedList<>();
    }
}
