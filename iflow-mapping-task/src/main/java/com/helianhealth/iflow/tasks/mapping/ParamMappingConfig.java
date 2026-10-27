package com.helianhealth.iflow.tasks.mapping;

import com.helianhealth.iflow.tasks.mapping.enums.MappingType;
import com.helianhealth.iflow.tasks.mapping.enums.ParamType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MAPPING 目标结构节点；对象字段、循环元素模板和固定元素均递归使用此类型。
 *
 * <p>数组顺序即同级字段顺序。
 */
public class ParamMappingConfig {

    /** payload 或循环变量上的取值路径，例如 $.items、item.code。 */
    private String sourceParamKey;

    /** 参数描述 */
    private String paramDesc;

    /** 目标参数名*/
    private String targetParamKey;

    /** 目标参数类型 */
    private ParamType targetParamType;

    /** 映射规则类型 */
    private MappingType mappingType;

    /** CONSTANT 的原生 JSON 值，或 EXPRESSION 的 JavaScript 字符串。 */
    private Object mappingRule;

    /** OBJECT 的子字段 */
    private List<ParamMappingConfig> childParams = new ArrayList<>();

    /** ARRAY_MAP 当前元素的变量名。 */
    private String itemAlias;

    /** ARRAY_MAP 元素模板 */
    private ParamMappingConfig itemTemplate;

    /** ARRAY_FIXED 的有序元素模板 */
    private List<ParamMappingConfig> items = new ArrayList<>();

    public String getSourceParamKey() {
        return this.sourceParamKey;
    }

    public String getParamDesc() {
        return this.paramDesc;
    }

    public String getTargetParamKey() {
        return this.targetParamKey;
    }

    public ParamType getTargetParamType() {
        return this.targetParamType;
    }

    public MappingType getMappingType() {
        return this.mappingType;
    }

    public Object getMappingRule() {
        return this.mappingRule;
    }

    public List<ParamMappingConfig> getChildParams() {
        return this.childParams;
    }

    public String getItemAlias() {
        return this.itemAlias;
    }

    public ParamMappingConfig getItemTemplate() {
        return this.itemTemplate;
    }

    public List<ParamMappingConfig> getItems() {
        return this.items;
    }

    public void setSourceParamKey(String sourceParamKey) {
        this.sourceParamKey = sourceParamKey;
    }

    public void setParamDesc(String paramDesc) {
        this.paramDesc = paramDesc;
    }

    public void setTargetParamKey(String targetParamKey) {
        this.targetParamKey = targetParamKey;
    }

    public void setTargetParamType(ParamType targetParamType) {
        this.targetParamType = targetParamType;
    }

    public void setMappingType(MappingType mappingType) {
        this.mappingType = mappingType;
    }

    public void setMappingRule(Object mappingRule) {
        this.mappingRule = mappingRule;
    }

    public void setChildParams(List<ParamMappingConfig> childParams) {
        this.childParams = childParams;
    }

    public void setItemAlias(String itemAlias) {
        this.itemAlias = itemAlias;
    }

    public void setItemTemplate(ParamMappingConfig itemTemplate) {
        this.itemTemplate = itemTemplate;
    }

    public void setItems(List<ParamMappingConfig> items) {
        this.items = items;
    }

    protected boolean canEqual(Object other) {
        return other instanceof ParamMappingConfig;
    }

    public ParamMappingConfig() {
    }

    public ParamMappingConfig(
            String sourceParamKey,
            String paramDesc,
            String targetParamKey,
            ParamType targetParamType,
            MappingType mappingType,
            Object mappingRule,
            List<ParamMappingConfig> childParams,
            String itemAlias,
            ParamMappingConfig itemTemplate,
            List<ParamMappingConfig> items) {
        this.sourceParamKey = sourceParamKey;
        this.paramDesc = paramDesc;
        this.targetParamKey = targetParamKey;
        this.targetParamType = targetParamType;
        this.mappingType = mappingType;
        this.mappingRule = mappingRule;
        this.childParams = childParams;
        this.itemAlias = itemAlias;
        this.itemTemplate = itemTemplate;
        this.items = items;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ParamMappingConfig other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getSourceParamKey(), other.getSourceParamKey())
                && Objects.equals(getParamDesc(), other.getParamDesc())
                && Objects.equals(getTargetParamKey(), other.getTargetParamKey())
                && Objects.equals(getTargetParamType(), other.getTargetParamType())
                && Objects.equals(getMappingType(), other.getMappingType())
                && Objects.equals(getMappingRule(), other.getMappingRule())
                && Objects.equals(getChildParams(), other.getChildParams())
                && Objects.equals(getItemAlias(), other.getItemAlias())
                && Objects.equals(getItemTemplate(), other.getItemTemplate())
                && Objects.equals(getItems(), other.getItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getSourceParamKey(),
                getParamDesc(),
                getTargetParamKey(),
                getTargetParamType(),
                getMappingType(),
                getMappingRule(),
                getChildParams(),
                getItemAlias(),
                getItemTemplate(),
                getItems());
    }

    @Override
    public String toString() {
        return "ParamMappingConfig(sourceParamKey=" + getSourceParamKey()
                + ", paramDesc=" + getParamDesc()
                + ", targetParamKey=" + getTargetParamKey()
                + ", targetParamType=" + getTargetParamType()
                + ", mappingType=" + getMappingType()
                + ", mappingRule=" + getMappingRule()
                + ", childParams=" + getChildParams()
                + ", itemAlias=" + getItemAlias()
                + ", itemTemplate=" + getItemTemplate()
                + ", items=" + getItems() + ")";
    }

    public static class ParamMappingConfigBuilder {

        private String sourceParamKey;
        private String paramDesc;
        private String targetParamKey;
        private ParamType targetParamType;
        private MappingType mappingType;
        private Object mappingRule;
        private List<ParamMappingConfig> childParams;
        private boolean childParamsSet;
        private String itemAlias;
        private ParamMappingConfig itemTemplate;
        private List<ParamMappingConfig> items;
        private boolean itemsSet;

        ParamMappingConfigBuilder() {
        }

        public ParamMappingConfigBuilder sourceParamKey(String sourceParamKey) {
            this.sourceParamKey = sourceParamKey;
            return this;
        }

        public ParamMappingConfigBuilder paramDesc(String paramDesc) {
            this.paramDesc = paramDesc;
            return this;
        }

        public ParamMappingConfigBuilder targetParamKey(String targetParamKey) {
            this.targetParamKey = targetParamKey;
            return this;
        }

        public ParamMappingConfigBuilder targetParamType(ParamType targetParamType) {
            this.targetParamType = targetParamType;
            return this;
        }

        public ParamMappingConfigBuilder mappingType(MappingType mappingType) {
            this.mappingType = mappingType;
            return this;
        }

        public ParamMappingConfigBuilder mappingRule(Object mappingRule) {
            this.mappingRule = mappingRule;
            return this;
        }

        public ParamMappingConfigBuilder childParams(List<ParamMappingConfig> childParams) {
            this.childParams = childParams;
            this.childParamsSet = true;
            return this;
        }

        public ParamMappingConfigBuilder itemAlias(String itemAlias) {
            this.itemAlias = itemAlias;
            return this;
        }

        public ParamMappingConfigBuilder itemTemplate(ParamMappingConfig itemTemplate) {
            this.itemTemplate = itemTemplate;
            return this;
        }

        public ParamMappingConfigBuilder items(List<ParamMappingConfig> items) {
            this.items = items;
            this.itemsSet = true;
            return this;
        }

        public ParamMappingConfig build() {
            return new ParamMappingConfig(
                    sourceParamKey,
                    paramDesc,
                    targetParamKey,
                    targetParamType,
                    mappingType,
                    mappingRule,
                    childParamsSet ? childParams : new ArrayList<>(),
                    itemAlias,
                    itemTemplate,
                    itemsSet ? items : new ArrayList<>());
        }

        @Override
        public String toString() {
            return "ParamMappingConfig.ParamMappingConfigBuilder(sourceParamKey=" + sourceParamKey
                    + ", paramDesc=" + paramDesc
                    + ", targetParamKey=" + targetParamKey
                    + ", targetParamType=" + targetParamType
                    + ", mappingType=" + mappingType
                    + ", mappingRule=" + mappingRule
                    + ", childParams=" + childParams
                    + ", itemAlias=" + itemAlias
                    + ", itemTemplate=" + itemTemplate
                    + ", items=" + items + ")";
        }
    }

    public static ParamMappingConfigBuilder builder() {
        return new ParamMappingConfigBuilder();
    }
}
