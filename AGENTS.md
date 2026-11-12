# rtk (Rust Token Killer) 使用指南

本项目已安装 rtk，二进制位于 `C:\Users\杨铭\bin\rtk.exe`。

## 启动（每次会话第一件事）

```powershell
$env:PATH = $env:PATH + ";C:\Users\杨铭\bin"
```

## 命令映射表

AI 执行终端命令时，必须用 rtk 包装以下命令：

| 原始命令 | 必须改为 | 节省 |
|----------|---------|------|
| `gradlew build` | `rtk gradlew build` | 97% |
| `gradlew test` | `rtk gradlew test` | 90% |
| `gradlew runData` | `rtk gradlew runData` | 95% |
| `git status` | `rtk git status` | 80% |
| `git diff` | `rtk git diff` | 75% |
| `git log` | `rtk git log` | 80% |
| `cat file` | `rtk read file` | 70% |
| `grep` | `rtk grep` | 80% |
| `find` | `rtk find` | 60% |

## 元命令

- `rtk gain` — 查看节省统计
- `rtk proxy <cmd>` — 不过滤原样执行

## 红线

- 映射操作禁止裸调用 gradlew/git/cat/grep/find
- 例外：git add/commit/push、Minecraft 启动、客户端测试可裸调用
