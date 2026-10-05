# SPI 扩展接入与命名迁移

SPI 公共 API 位于 `com.helianhealth.iflow.common.extension`，统一使用不带 `Agent` 前缀的名称。

扩展接口通过 `@SPI` 标注，可指定默认扩展名：

```java
import com.helianhealth.iflow.common.extension.SPI;

@SPI("default")
public interface MyExtension {
    String execute(String input);
}
```

在 `src/main/resources/META-INF/iflow/{接口全限定名}` 中声明实现，例如文件 `META-INF/iflow/com.example.MyExtension`：

```properties
default=com.example.DefaultMyExtension
```

通过 `ExtensionLoader.getExtensionLoader(MyExtension.class).getDefaultExtension()` 获取默认实现，或通过 `getExtension("扩展名")` 获取指定实现。`@Adaptive` 用于自适应选择，`@Activate` 用于按条件激活扩展。

## 旧命名迁移

| 旧名称 | 新名称 |
| --- | --- |
| `AgentSPI` | `SPI` |
| `AgentAdaptive` | `Adaptive` |
| `AgentActivate` | `Activate` |
| `AgentExtensionLoader` | `ExtensionLoader` |
| `AgentExtensionFactory` | `ExtensionFactory` |
| `AgentAdaptiveExtensionFactory` | `AdaptiveExtensionFactory` |
| `AgentSpiExtensionFactory` | `SpiExtensionFactory` |
| `SpringAgentExtensionFactory` | `SpringExtensionFactory` |

工厂实现仍位于 `com.helianhealth.iflow.common.extension.factory`。

这是一次公共 API 和资源路径变更。外部扩展需要更新 Java 导入、注解和类名引用，并将配置文件从 `META-INF/agent/` 移到 `META-INF/iflow/`。在线动态扩展的源码若引用了旧 API，也需要同步更新。配置文件名仍使用接口全限定名，内容仍使用 `扩展名=实现类全限定名`；接口或实现类改名时，需要同步更新文件名或内容。旧类名和旧目录不再提供兼容入口，更新后应清理旧构建产物并重新编译。
