---
name: spi-extension
description: 为无法配置化的场景生成 SPI 扩展代码，覆盖四种 ExtensionType 及 PROJECT/ONLINE 注册方式
---

# SPI 扩展 Skill

> 当自动化配置（节点 + 参数映射 + Mock）无法完成对接时，编写 SPI 扩展代码。

---

## 一、何时需要 SPI（与配置化的边界）

**优先走配置化**（见 `auto-config` skill）：`interfaceUri`、节点 `nodeType` + `metaInfo`、PRE/POST 参数映射、SpEL 过滤/调度/拆批、`responseType`、XML `contentMetaInfo` 等。

**必须 SPI** 的典型场景：

| 场景 | 推荐扩展类型 |
|------|-------------|
| 外呼需自定义签名、Token、加解密、非标准 HTTP 流程 | `remote`（或 `filter`，见下） |
| 需在标准外呼链上统一改请求头、Mock 拦截、前后置处理 | `filter` |
| Open API 入口协议/编排与默认不一致 | `workflow` |
| Jarvis 入口协议/编排与默认不一致 | `jarvis-workflow` |

**选型提示**：

- 只需在**已有参数映射 + 协议客户端**基础上加签名/Token → 优先 `filter`（包在 `InterfaceClientProxy` 外，仍走默认参数树）。
- 需**完全接管**某节点的调用流程（自己组请求、调 HTTP、转响应）→ 用 `remote`（替换 `DefaultInterfaceRemoteInvoker`，不再自动走 PRE/POST 映射，除非自己在代码里实现）。

---

## 二、框架提供的四种扩展类型

来源：`ExtensionType` 枚举。SPI 机制基于 `AgentExtensionLoader`（类 Dubbo ExtensionLoader）。

| code | 名称 | SPI 接口 | 绑定对象 | 触发字段 | 默认实现 |
|------|------|----------|----------|----------|----------|
| `remote` | 远程调用扩展 | `com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker` | 工作流节点（可多节点） | 节点 `remoteExtension` | `DefaultInterfaceRemoteInvoker` |
| `filter` | 过滤器扩展 | `com.helianhealth.agent.invocation.remote.filter.AgentFilter` | 工作流节点（可多节点） | 节点 `extensionParam` 中 `filter.{扩展名}` | 内置 `MockAgentFilter` 等 |
| `workflow` | Open API 工作流扩展 | `com.helianhealth.agent.api.OpenApiRouteHandler` | 工作流（单 flowId） | 工作流 `routeExtension` | `DefaultOpenApiRouteHandler` |
| `jarvis-workflow` | Jarvis 工作流扩展 | `com.helianhealth.agent.api.JarvisRouteHandler` | 工作流（单 flowId） | 工作流 `routeExtension` | `DefaultJarvisRouteHandler` |

---

## 三、如何注册扩展

扩展配置持久化在表 `spi_extension_config`（领域模型 `NodeExtensionConfig`）。注册后框架会同步写关联字段（`remote_extension` / `extension_param` / `route_extension`）并加载到 SPI 运行时。

### 3.1 两种扩展方式

| 方式 | extensionMode | 适用 | 说明 |
|------|---------------|------|------|
| **项目扩展** | `PROJECT` | 复杂逻辑、需 Spring 注入、可引第三方库 | 类放在 classpath（如 `agent-web/.../extension/`），注册时填 `className`，**无需** `sourceCode`；需重启或重新注册后生效 |
| **在线扩展** | `ONLINE` | 快速调试、简单逻辑 | 控制台/MCP 提交 Java 源码，运行时动态编译，**无需重启** |

> 内置扩展（如 `default`、`http`、`mock`）通过 `META-INF/agent/{SPI接口全限定名}` 静态声明，与上述两种用户扩展方式独立。

### 3.2 注册入口

- **控制台**：工作流节点扩展 / 过滤器扩展 / 工作流扩展 三个管理页。
- **MCP Tool**：`agent_spi_extension`，`action`: `list` / `register` / `update` / `delete` / `enable` / `disable`；`type` 取上表 `code`。

注册时 `bindingIds` 含义：

- `remote` / `filter`：节点 ID 列表，如 `[8, 9]`
- `workflow` / `jarvis-workflow`：工作流 ID 列表（仅第一个生效），如 `[3]`

---

## 四、各类型写代码要点

### 4.1 remote — `InterfaceRemoteInvoker`

**接口**：

```java
@AgentSPI("default")
public interface InterfaceRemoteInvoker {
    @AgentAdaptive("remoteExtension")
    Map<String, Object> remoteInvoke(WorkflowNode flowNode, Invocation invocation);
}
```

**职责**：完整实现「入参转换 → 外呼 → 响应转换」。框架外层已有 `InterfaceRemoteInvokerLogWrapper` 负责上游入参/最终响应/耗时日志；扩展内用 `LogCollector` 记录外呼细节。

**可用工具**：

| 类 | 用途 |
|----|------|
| `invocation.getBusinessData()` | 上游业务入参 |
| `flowNode.getMetaInfo()` / `JsonUtils.parseMetaInfo()` | 节点协议配置（如 url） |
| `flowNode.getExtensionParam()` | 扩展参数（密钥等） |
| `HttpUtils` | HTTP GET/POST/Form |
| `JsonUtils` | JSON 序列化/反序列化 |
| `LogCollector.collectInvokeRequest/Response/InvokeTime/Error` | 外呼过程日志 |

**模板**（扩展名 `my-invoker`，节点 `remoteExtension: "my-invoker"`）：

```java
package com.helianhealth.agent.extension;

import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.invocation.support.HttpUtils;
import com.helianhealth.agent.invocation.support.LogCollector;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MyInvoker implements InterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(WorkflowNode flowNode, Invocation invocation) {
        Map<String, Object> businessData = invocation.getBusinessData();
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> request = convertRequest(businessData);
            String requestJson = JsonUtils.toJsonString(request);
            LogCollector.collectInvokeRequest(requestJson);

            Map<String, Object> metaInfo = JsonUtils.parseMetaInfo(flowNode.getMetaInfo());
            String url = (String) metaInfo.get("url");

            String responseJson = HttpUtils.post(url, requestJson);
            LogCollector.collectInvokeResponse(responseJson);

            return convertResponse(JsonUtils.parseObjectMap(responseJson));
        } catch (Exception e) {
            log.error("远程调用失败", e);
            LogCollector.collectError(e.getMessage());
            Map<String, Object> err = new HashMap<>();
            err.put("code", "500");
            err.put("message", e.getMessage());
            return err;
        } finally {
            LogCollector.collectInvokeTime(System.currentTimeMillis() - startTime);
        }
    }

    private Map<String, Object> convertRequest(Map<String, Object> upstream) {
        // 字段映射
        return new HashMap<>(upstream);
    }

    private Map<String, Object> convertResponse(Map<String, Object> downstream) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", downstream.get("code"));
        result.put("message", downstream.get("message"));
        return result;
    }
}
```

**PROJECT 模式可注入 Spring Bean**（参考 `TestInvoker` 注入 `JdbcTemplate`）：构造器注入，框架通过 `AgentExtensionFactory` 自动解析依赖。

---

### 4.2 filter — `AgentFilter`

**接口**：

```java
@AgentSPI
public interface AgentFilter {
    Map<String, Object> doInvoke(InterfaceClientProxy next, WorkflowNode flowNode, List<ParamTreeNode> params);
}
```

**职责**：在 `InterfaceClientProxy.doInvoke` 之前/之后拦截，典型用于动态请求头、签名、Mock。责任链模式：不拦截时调用 `next.doInvoke(flowNode, params)`。

**激活条件**：

1. 注册 filter 扩展时，框架自动写节点 `extensionParam`：`filter.{扩展名} = {extensionParamValue}`（默认 `"true"`）。
2. 实现类需标注 `@AgentActivate(value = "filter.{扩展名}")`，与上述 key 对应。
3. 内置 `MockAgentFilter`（`filter.mock`）、`HeaderAgentFilter`（`filter.header`）走同样机制。

**模板**（扩展名 `my-filter`）：

```java
package com.helianhealth.agent.extension;

import com.helianhealth.agent.common.extension.AgentActivate;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.common.base.MetaInfoKeyConstants;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;

import java.util.List;
import java.util.Map;

@AgentActivate(value = "filter.my-filter", order = 50)
public class MyFilter implements AgentFilter {

    @Override
    public Map<String, Object> doInvoke(InterfaceClientProxy next, WorkflowNode flowNode, List<ParamTreeNode> params) {
        // 例：向 metaInfo.headers 追加签名头
        Map<String, Object> metaInfo = JsonUtils.parseMetaInfo(flowNode.getMetaInfo());
        // ... 修改 headers ...
        flowNode.setMetaInfo(JsonUtils.toJsonString(metaInfo));
        return next.doInvoke(flowNode, params);
    }
}
```

> 继承 `AbstractHeaderAgentFilter` 可复用请求头修改模板（检查 `filter.header=true` 时生效）。

---

### 4.3 workflow — `OpenApiRouteHandler`

**接口**：

```java
@AgentSPI("default")
public interface OpenApiRouteHandler {
    @AgentAdaptive("routeExtension")
    String handle(Workflow workflow, String requestBody);
}
```

**职责**：接管 Open API 入口。默认实现会解析请求 → 调度工作流节点 → 拼装响应；自定义实现需自行完成全流程或按需委托。

**日志**：无节点上下文时用 `LogCollector.saveInvokeLog(...)` 直接入库。

**模板**：

```java
package com.helianhealth.agent.extension;

import com.helianhealth.agent.api.OpenApiRouteHandler;
import com.helianhealth.agent.repository.model.domain.Workflow;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyOpenApiHandler implements OpenApiRouteHandler {

    @Override
    public String handle(Workflow workflow, String requestBody) {
        log.info("自定义 Open API 处理, uri={}", workflow.getInterfaceUri());
        // TODO: 解析 requestBody → 调度 → 返回响应字符串
        return "{}";
    }
}
```

注册后框架将工作流 `routeExtension` 设为扩展名；请求命中该工作流时路由到此实现。

---

### 4.4 jarvis-workflow — `JarvisRouteHandler`

**接口**：

```java
@AgentSPI("default")
public interface JarvisRouteHandler {
    @AgentAdaptive("routeExtension")
    JarvisResultData<Object> handle(Workflow workflow, JarvisRequest request);
}
```

**职责**：接管 Jarvis 入口，返回 `JarvisResultData`。绑定与触发方式同 `workflow`，区别在 SPI 接口和请求/响应模型。

---

## 五、生成代码后的输出格式

```
✅ SPI 扩展代码已生成

扩展类型：remote | filter | workflow | jarvis-workflow
扩展名称：my-invoker
实现类：com.helianhealth.agent.extension.MyInvoker
扩展方式：PROJECT | ONLINE

使用方式：
1. PROJECT：代码放到 agent-web/src/main/java/com/helianhealth/agent/extension/，通过控制台或 MCP 注册（extensionMode=PROJECT）
2. ONLINE：通过控制台或 MCP 直接提交源码注册（extensionMode=ONLINE）
3. 注册时指定 bindingIds，框架自动写入 remoteExtension / extensionParam / routeExtension
```
