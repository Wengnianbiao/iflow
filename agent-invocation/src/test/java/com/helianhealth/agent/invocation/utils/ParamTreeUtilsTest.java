package com.helianhealth.agent.invocation.utils;

import com.alibaba.fastjson2.JSONArray;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ParamTreeUtilsTest {

    @Test
    public void testParamNodeDTO2Xml() {

        // 组装code标签
        ParamTreeNode value = ParamTreeNode.builder()
                .paramKey("value")
                .paramValue("血清")
                .paramType(ParamType.STRING)
                .build();
        List<ParamTreeNode> valueList = new ArrayList<>();
        valueList.add(value);
        ParamTreeNode displayName = ParamTreeNode.builder()
                .paramKey("displayName")
                .paramValue(null)
                .paramType(ParamType.OBJECT)
                .attributeNodes(valueList)
                .build();

        List<ParamTreeNode> displayNameList = new ArrayList<>();
        displayNameList.add(displayName);

        ParamTreeNode codeAttribute = ParamTreeNode.builder()
                .paramKey("code")
                .paramValue("xq")
                .paramType(ParamType.STRING)
                .build();
        List<ParamTreeNode> codeAttributeList = new ArrayList<>();
        codeAttributeList.add(codeAttribute);

        // code标签finished
        ParamTreeNode codeElement = ParamTreeNode.builder()
                .paramKey("code")
                .paramType(ParamType.OBJECT)
                .attributeNodes(codeAttributeList)
                .children(displayNameList)
                .build();

        // 组装id标签体
        ParamTreeNode rootAttribute = ParamTreeNode.builder()
                .paramKey("root")
                .paramValue("2.16.156.10011.1.14")
                .paramType(ParamType.STRING)
                .build();
        ParamTreeNode extensionAttribute = ParamTreeNode.builder()
                .paramKey("extension")
                .paramValue("800000218793")
                .paramType(ParamType.STRING)
                .build();
        List<ParamTreeNode> idAttributeList = new ArrayList<>();
        idAttributeList.add(rootAttribute);
        idAttributeList.add(extensionAttribute);

        // id标签finished
        ParamTreeNode idElement = ParamTreeNode.builder()
                .paramKey("id")
                .paramType(ParamType.OBJECT)
                .attributeNodes(idAttributeList)
                .build();

        ParamTreeNode classCodeAttribute = ParamTreeNode.builder()
                .paramKey("classCode")
                .paramValue("SPEC")
                .paramType(ParamType.STRING)
                .attributeNodes(idAttributeList)
                .build();
        List<ParamTreeNode> specimenSonAttributeList = new ArrayList<>();
        specimenSonAttributeList.add(classCodeAttribute);

        List<ParamTreeNode> specimenSonChildList = new ArrayList<>();
        specimenSonChildList.add(idElement);
        specimenSonChildList.add(codeElement);
        ParamTreeNode specimenSonElement = ParamTreeNode.builder()
                .paramKey("specimen")
                .paramValue(null)
                .attributeNodes(specimenSonAttributeList)
                .paramType(ParamType.OBJECT)
                .children(specimenSonChildList)
                .build();

        List<ParamTreeNode> specimenFatherChildList = new ArrayList<>();
        specimenFatherChildList.add(specimenSonElement);
        ParamTreeNode specimenFatherElement = ParamTreeNode.builder()
                .paramKey("specimen")
                .paramValue(null)
                .paramType(ParamType.OBJECT)
                .children(specimenFatherChildList)
                .build();
    }

    @Test
    void convertToJsonFormatMap_shouldFlattenSingleElementArrayWhenKeepSingleElementKeyIsFalse() {
        ParamTreeNode itemsValue = ParamTreeNode.builder()
                .paramKey("itemsValue")
                .paramType(ParamType.STRING)
                .paramValue("F00000047229")
                .build();

        ParamTreeNode itemsNode = ParamTreeNode.builder()
                .paramKey("itemsNode")
                .paramType(ParamType.OBJECT)
                .keepSingleElementKey(false)
                .children(Collections.singletonList(itemsValue))
                .build();

        ParamTreeNode items = ParamTreeNode.builder()
                .paramKey("items")
                .paramType(ParamType.ARRAY)
                .children(Collections.singletonList(itemsNode))
                .build();

        Map<String, Object> result = ParamNodeUtils.convertToJsonFormatMap(Collections.singletonList(items));

        Object itemsResult = result.get("items");
        assertInstanceOf(JSONArray.class, itemsResult);
        JSONArray itemsArray = (JSONArray) itemsResult;
        assertEquals(1, itemsArray.size());
        assertEquals("F00000047229", itemsArray.get(0));
    }

    @Test
    void convertToJsonFormatMap_shouldKeepSingleElementKeyWhenConfigured() {
        ParamTreeNode itemsValue = ParamTreeNode.builder()
                .paramKey("itemsValue")
                .paramType(ParamType.STRING)
                .paramValue("F00000047229")
                .build();

        ParamTreeNode itemsNode = ParamTreeNode.builder()
                .paramKey("itemsNode")
                .paramType(ParamType.OBJECT)
                .keepSingleElementKey(true)
                .children(Collections.singletonList(itemsValue))
                .build();

        ParamTreeNode items = ParamTreeNode.builder()
                .paramKey("items")
                .paramType(ParamType.ARRAY)
                .children(Collections.singletonList(itemsNode))
                .build();

        Map<String, Object> result = ParamNodeUtils.convertToJsonFormatMap(Collections.singletonList(items));

        Object itemsResult = result.get("items");
        assertInstanceOf(JSONArray.class, itemsResult);
        JSONArray itemsArray = (JSONArray) itemsResult;
        assertEquals(1, itemsArray.size());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("itemsValue", "F00000047229");
        assertEquals(expected, itemsArray.get(0));
    }

    @Test
    void convertToJsonFormatMap_shouldWrapMultiFieldArrayElementsAsObject() {
        ParamTreeNode itemCode = ParamTreeNode.builder()
                .paramKey("itemCode")
                .paramType(ParamType.STRING)
                .paramValue("F00000047229")
                .build();
        ParamTreeNode itemName = ParamTreeNode.builder()
                .paramKey("itemName")
                .paramType(ParamType.STRING)
                .paramValue("耗材A")
                .build();

        List<ParamTreeNode> itemFields = new ArrayList<>();
        itemFields.add(itemCode);
        itemFields.add(itemName);

        ParamTreeNode itemsNode = ParamTreeNode.builder()
                .paramKey("itemsNode")
                .paramType(ParamType.OBJECT)
                .keepSingleElementKey(false)
                .children(itemFields)
                .build();

        ParamTreeNode items = ParamTreeNode.builder()
                .paramKey("items")
                .paramType(ParamType.ARRAY)
                .children(Collections.singletonList(itemsNode))
                .build();

        Map<String, Object> result = ParamNodeUtils.convertToJsonFormatMap(Collections.singletonList(items));

        Object itemsResult = result.get("items");
        assertInstanceOf(JSONArray.class, itemsResult);
        JSONArray itemsArray = (JSONArray) itemsResult;
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("itemCode", "F00000047229");
        expected.put("itemName", "耗材A");
        assertEquals(expected, itemsArray.get(0));
    }
}
