## 工具链规则：rtk（Rust Token Killer）

rtk 已安装在本项目环境，二进制位于 `C:\Users\杨铭\bin\rtk.exe`，已在用户 PATH 中。

### 启动检查（每次会话开始时执行一次）

```powershell
$env:PATH = $env:PATH + ";C:\Users\杨铭\bin"
```
确认 rtk 可用：
```powershell
rtk --version
```

### 强制命令映射

AI 执行 Bash/终端命令时，以下命令必须使用 rtk 包装，禁止裸调用：

| 原始命令 | 必须改为 | 节省效果 |
|----------|---------|---------|
| `gradlew build` | `rtk gradlew build` | ~97% |
| `gradlew test` | `rtk gradlew test` | ~90% |
| `gradlew runData` | `rtk gradlew runData` | ~95% |
| `git status` | `rtk git status` | ~80% |
| `git diff` | `rtk git diff` | ~75% |
| `git log` | `rtk git log` | ~80% |
| `cat file.java` | `rtk read file.java` | ~70% |
| `grep "pattern"` | `rtk grep "pattern"` | ~80% |
| `find . -name "*.java"` | `rtk find "*.java" .` | ~60% |

### 红线规则

- 禁止直接调用 `gradlew`、`gradle.bat`、`git`（裸命令）执行上述映射操作
- 禁止直接用 Read 工具读取 .java 文件（用 `rtk read` 替代）
- 禁止直接用 Grep 工具搜索（用 `rtk grep` 替代）
- 例外情况：`git add`、`git commit`、`git push` 可以裸调用；Minecraft 启动、Mod 加载、客户端测试等 rtk 不支持的命令可裸调用

### rtk 元命令（直接使用，无需包装）

```powershell
rtk gain              # 显示 token 节省统计
rtk gain --history    # 显示历史命令及节省量
rtk proxy <cmd>       # 不过滤，原样执行（调试用）
rtk discover          # 分析历史，发现漏用 rtk 的机会
```
