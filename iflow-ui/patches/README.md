# Monaco 0.55.1 生命周期补丁

`monaco-editor@0.55.1.patch` 将全局 hover delegate 工厂绑定到应用级
`StandaloneServices`，避免保存差异编辑器销毁后，其他编辑器通过补全、查找或右键菜单
访问已经销毁的局部 `InstantiationService`。

对应上游问题：<https://github.com/microsoft/monaco-editor/issues/5518>。
补丁通过 `package.json` 的 `pnpm.patchedDependencies` 和锁文件随 `pnpm install` 应用。

回归验证：运行 `pnpm dev`，打开
<http://127.0.0.1:5174/tests/monacoLifecycle.html>，点击“运行回归检查”。
页面无需后端，覆盖差异编辑器关闭后的首次补全、查找、右键菜单，以及真实保存弹窗
重复开关、页面卸载时的模型释放。React 弹窗检查使用 StrictMode。

升级 Monaco 时，先在不带此补丁的版本运行回归页，确认问题已修复后再删除补丁。
