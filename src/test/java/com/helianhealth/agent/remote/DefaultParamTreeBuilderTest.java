package com.helianhealth.agent.remote;

import com.helianhealth.agent.enums.MappingSource;
import com.helianhealth.agent.enums.MappingType;
import com.helianhealth.agent.enums.ParamType;
import com.helianhealth.agent.enums.ProcessType;
import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.resolver.BeanExpressionValueResolver;
import com.helianhealth.agent.remote.resolver.ConstantValueResolver;
import com.helianhealth.agent.remote.resolver.ExpressionValueResolver;
import com.helianhealth.agent.remote.resolver.NameValueResolver;
import com.helianhealth.agent.remote.resolver.SingleMapResolveService;
import com.helianhealth.agent.remote.resolver.ValueResolver;
import com.helianhealth.agent.remote.resolver.ValueResolverSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class DefaultParamTreeBuilderTest {

    private DefaultParamTreeBuilder treeBuilder;

    @BeforeEach
    public void setUp() {
        // mock各种解析器
        treeBuilder = new DefaultParamTreeBuilder();
        List<ValueResolver> resolvers = new ArrayList<>();
        resolvers.add(new NameValueResolver());
        resolvers.add(new SingleMapResolveService());
        resolvers.add(new ConstantValueResolver());
        resolvers.add(new ExpressionValueResolver());
        resolvers.add(new BeanExpressionValueResolver());
        ValueResolverSelector valueResolverSelector = new ValueResolverSelector(resolvers);
        treeBuilder.setValueResolverSelector(valueResolverSelector);
    }

    @Test
    public void testBuildSimpleParamTree() {
        List<NodeParamConfigDO> configs = new ArrayList<>();

        // 添加简单的字符串参数配置
        NodeParamConfigDO patientCodeConfig = new NodeParamConfigDO();
        patientCodeConfig.setProcessType(ProcessType.PRE_PROCESS);
        patientCodeConfig.setSourceParamKey("patientCode");
        patientCodeConfig.setSourceParamType(ParamType.STRING);
        patientCodeConfig.setParamDesc("体检编号");
        patientCodeConfig.setTargetParamKey("patientCode");
        patientCodeConfig.setTargetParamType(ParamType.STRING);
        patientCodeConfig.setMappingType(MappingType.NAME);
        patientCodeConfig.setMappingSource(MappingSource.INPUT);
        patientCodeConfig.setSort(1);
        configs.add(patientCodeConfig);

        // 添加简单的整数参数配置
        NodeParamConfigDO ageConfig = new NodeParamConfigDO();
        ageConfig.setProcessType(ProcessType.PRE_PROCESS);
        ageConfig.setSourceParamKey("age");
        ageConfig.setSourceParamType(ParamType.INTEGER);
        ageConfig.setParamDesc("年龄");
        ageConfig.setTargetParamKey("age");
        ageConfig.setTargetParamType(ParamType.INTEGER);
        ageConfig.setMappingType(MappingType.NAME);
        ageConfig.setSort(1);
        ageConfig.setMappingSource(MappingSource.INPUT);
        configs.add(ageConfig);

        // 添加表达式参数配置
        NodeParamConfigDO sexConfig = new NodeParamConfigDO();
        sexConfig.setProcessType(ProcessType.PRE_PROCESS);
        sexConfig.setSourceParamKey("sex");
        sexConfig.setSourceParamType(ParamType.STRING);
        sexConfig.setParamDesc("性别（0/null/3未知，1男，2女）");
        sexConfig.setTargetParamKey("sex");
        sexConfig.setTargetParamType(ParamType.STRING);
        sexConfig.setMappingType(MappingType.EXPRESSION);
        sexConfig.setMappingSource(MappingSource.INPUT);
        sexConfig.setSort(1);
        sexConfig.setMappingRule("(#data == null || #data == '' ) ? '未知' : (#data == '1' ? '男' : (#data == '2' ? '女' : '未知'))");
        configs.add(sexConfig);

        // 构建测试数据
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("patientCode", "102509080001");
        businessData.put("age", 25);
        businessData.put("sex", "1");

        // 构建基础数据类型参数树
//        List<ParamTreeNode> result = treeBuilder.buildParamTree(configs, null, businessData, businessData, businessData);
//
//        // 添加断言验证结果
//        assertNotNull(result);
//        assertFalse(result.isEmpty());
    }

    @Test
    public void testBuildObjectParamTree() {
        // 构建对象参数树
    }

    @Test
    public void testBuildArrayParamTree() {
        // 构建数组参数树
    }
}
