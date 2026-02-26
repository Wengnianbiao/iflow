# SPI 扩展 Skill

> 为无法配置化的接口场景生成 SPI 扩展代码

---

## 触发条件

- 可行性评估结果为"需要 SPI 扩展"
- 用户要求编写扩展代码
- 常见场景：动态签名、动态 Token、特殊加密

---

## SPI 扩展机制

### 核心接口

```java
@AgentSPI("default")
public interface InterfaceRemoteInvoker {
    @AgentAdaptive("remoteExtension")
    Map<String, Object> remoteInvoke(InterfaceWorkflowNode flowNode, Invocation invocation);
}
```

### 扩展方式

1. **继承默认实现**：`extends DefaultInterfaceRemoteInvoker`
2. **重写 remoteInvoke 方法**：实现自定义逻辑
3. **节点配置**：`remoteExtension` 字段指定扩展名

---

## 代码模板

### 动态签名扩展

```java
package com.helianhealth.agent.extension;

import com.helianhealth.agent.invocation.remote.DefaultInterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * 动态签名扩展
 * 使用方式：节点 remoteExtension 配置为 "dynamic-sign"
 */
@Slf4j
public class DynamicSignInvoker extends DefaultInterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(InterfaceWorkflowNode flowNode, Invocation invocation) {
        // 1. 获取扩展参数（从节点 extensionParam 获取）
        Map<String, String> extParams = flowNode.getExtensionParam();
        String secretKey = extParams.getOrDefault("secretKey", "default_secret");
        
        // 2. 前置处理：获取请求参数
        List<ParamTreeNode> params = preProcess(flowNode, invocation);
        String requestBody = JsonUtils.toJsonString(params);
        
        // 3. 生成签名
        long timestamp = System.currentTimeMillis();
        String sign = generateSign(requestBody, timestamp, secretKey);
        
        // 4. 添加签名到请求头（修改 metaInfo）
        addSignatureHeader(flowNode, sign, timestamp);
        
        // 5. 调用父类方法执行实际请求
        return super.remoteInvoke(flowNode, invocation);
    }
    
    private String generateSign(String body, long timestamp, String secret) {
        try {
            String content = body + timestamp + secret;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("签名生成失败", e);
        }
    }
    
    private void addSignatureHeader(InterfaceWorkflowNode flowNode, String sign, long timestamp) {
        // 解析并修改 metaInfo 中的 headers
        Map<String, Object> metaInfo = JsonUtils.toMap(flowNode.getMetaInfo());
        Map<String, String> headers = (Map<String, String>) metaInfo.getOrDefault("headers", new HashMap<>());
        headers.put("X-Sign", sign);
        headers.put("X-Timestamp", String.valueOf(timestamp));
        metaInfo.put("headers", headers);
        flowNode.setMetaInfo(JsonUtils.toJsonString(metaInfo));
    }
}
```

### 动态 Token 扩展

```java
package com.helianhealth.agent.extension;

import com.helianhealth.agent.invocation.remote.DefaultInterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 Token 扩展
 * 使用方式：节点 remoteExtension 配置为 "dynamic-token"
 */
@Slf4j
public class DynamicTokenInvoker extends DefaultInterfaceRemoteInvoker {

    private static final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, Object> remoteInvoke(InterfaceWorkflowNode flowNode, Invocation invocation) {
        // 1. 获取扩展参数
        Map<String, String> extParams = flowNode.getExtensionParam();
        String tokenUrl = extParams.get("tokenUrl");
        String clientId = extParams.get("clientId");
        String clientSecret = extParams.get("clientSecret");
        
        // 2. 获取或刷新 Token
        String token = getValidToken(tokenUrl, clientId, clientSecret);
        
        // 3. 添加 Token 到请求头
        addTokenHeader(flowNode, token);
        
        // 4. 执行实际请求
        return super.remoteInvoke(flowNode, invocation);
    }
    
    private String getValidToken(String tokenUrl, String clientId, String clientSecret) {
        String cacheKey = clientId + "_" + tokenUrl;
        TokenInfo cached = tokenCache.get(cacheKey);
        
        // Token 有效则直接返回
        if (cached != null && !cached.isExpired()) {
            return cached.token;
        }
        
        // 请求新 Token
        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        params.put("grant_type", "client_credentials");
        
        Map<String, Object> response = restTemplate.postForObject(tokenUrl, params, Map.class);
        String token = (String) response.get("access_token");
        int expiresIn = (Integer) response.getOrDefault("expires_in", 3600);
        
        // 缓存 Token
        tokenCache.put(cacheKey, new TokenInfo(token, expiresIn));
        return token;
    }
    
    private void addTokenHeader(InterfaceWorkflowNode flowNode, String token) {
        Map<String, Object> metaInfo = JsonUtils.toMap(flowNode.getMetaInfo());
        Map<String, String> headers = (Map<String, String>) metaInfo.getOrDefault("headers", new HashMap<>());
        headers.put("Authorization", "Bearer " + token);
        metaInfo.put("headers", headers);
        flowNode.setMetaInfo(JsonUtils.toJsonString(metaInfo));
    }
    
    private static class TokenInfo {
        String token;
        long expireTime;
        
        TokenInfo(String token, int expiresIn) {
            this.token = token;
            this.expireTime = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
```

### 特殊加密扩展

```java
package com.helianhealth.agent.extension;

import com.helianhealth.agent.invocation.remote.DefaultInterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AES 加密扩展
 * 使用方式：节点 remoteExtension 配置为 "aes-encrypt"
 */
@Slf4j
public class AesEncryptInvoker extends DefaultInterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(InterfaceWorkflowNode flowNode, Invocation invocation) {
        // 1. 获取加密密钥
        Map<String, String> extParams = flowNode.getExtensionParam();
        String aesKey = extParams.get("aesKey");
        
        // 2. 前置处理
        List<ParamTreeNode> params = preProcess(flowNode, invocation);
        String requestBody = JsonUtils.toJsonString(params);
        
        // 3. 加密请求体
        String encrypted = encrypt(requestBody, aesKey);
        
        // 4. 修改业务数据为加密后的数据
        invocation.getBusinessData().clear();
        invocation.getBusinessData().put("encryptedData", encrypted);
        
        // 5. 执行请求
        Map<String, Object> response = super.remoteInvoke(flowNode, invocation);
        
        // 6. 解密响应（如果需要）
        if (response.containsKey("encryptedResponse")) {
            String decrypted = decrypt((String) response.get("encryptedResponse"), aesKey);
            return JsonUtils.toMap(decrypted);
        }
        
        return response;
    }
    
    private String encrypt(String content, String key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(content.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    
    private String decrypt(String content, String key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(content));
            return new String(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
```

---

## 节点配置示例

```json
{
  "nodeName": "需要签名的HIS接口",
  "nodeType": "HTTP",
  "metaInfo": "{\"url\":\"http://his.example.com/api\"}",
  "remoteExtension": "dynamic-sign",
  "extensionParam": "{\"secretKey\":\"your_secret_key\"}"
}
```

---

## 扩展注册

### 方式一：项目代码注册

将扩展类放到 `agent-web/src/main/java/com/helianhealth/agent/extension/` 目录，框架会自动扫描加载。

### 方式二：在线注册（通过控制台）

使用节点扩展配置表 `node_extension_config` 注册：

```sql
INSERT INTO node_extension_config 
(node_id, spi_interface, extension_name, class_name, source_code, enabled, extension_mode)
VALUES 
('[8]', 
 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker',
 'dynamic-sign',
 'com.helianhealth.agent.extension.DynamicSignInvoker',
 '-- Java源码 --',
 1,
 'ONLINE');
```

---

## 生成代码输出

```
✅ SPI 扩展代码已生成！

扩展名称：dynamic-sign
扩展类：DynamicSignInvoker
功能：动态 MD5 签名

使用方式：
1. 将代码放到 agent-web/src/main/java/com/helianhealth/agent/extension/
2. 节点配置 remoteExtension: "dynamic-sign"
3. 节点配置 extensionParam: {"secretKey": "xxx"}
```
