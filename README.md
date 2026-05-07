# Codex Chat for IntelliJ IDEA

一个最小可运行的 IntelliJ IDEA 插件，把 Codex 对话集成到 IDE 的 Tool Window 中。

## 功能

- 在 IDEA 内直接和本机 `codex` CLI 对话。
- 一键附加当前编辑器选中的代码，包含文件路径、语言和行号。
- 一键附加 Run/Debug Console 的选中文本；如果没有选中文本，会尝试读取控制台末尾日志。
- 后台执行 `codex exec --skip-git-repo-check -C <project> -`，避免阻塞 UI。

## 使用前提

- 已安装并登录 Codex CLI，命令 `codex --version` 可用。
- 使用 JDK 17 或更高版本导入 Gradle 工程。

## 运行

在 IntelliJ IDEA 中打开本目录，然后运行 Gradle 任务：

```powershell
.\gradlew runIde
```

如果没有 Gradle Wrapper，可以直接在 IDEA 中导入 Gradle 项目，IDEA 会使用配置的 Gradle/JDK 下载依赖。

## 说明

初版通过本机 Codex CLI 通信，不在插件中保存 API Key。Codex 的实际权限、模型和登录状态由本机 Codex CLI 配置决定。
