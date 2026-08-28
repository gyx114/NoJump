# NoJump · 拦截 App 恶意跳转

一个安卓原生应用，通过 **Shizuku 免 root 系统权限**，在检测到"从来源应用跳转到目标应用"时，把目标应用整体**冻结**，从源头阻止短剧类 App 里那些"一点就跳到美团/起点读书"的恶意广告跳转。

> 技术栈：Kotlin + Jetpack Compose + Material3；**无障碍服务**实时侦测前台 + **Shizuku** 免 root 冻结。

## 核心原理

```
你在来源App(如短剧)里 → 广告拉起目标App(如美团)
        │
        ▼  NoJump 借 Shizuku 执行
   pm disable-user 美团   ← 目标被系统禁用，拉不起来
        │
  离开来源App             ← 稍候自动
        ▼
   pm enable 美团         ← 恢复正常
```

- **来源（source）**：进入这些 App 时才触发拦截（如红果短剧）
- **目标（target）**：被冻结拦截的应用（如美团、起点读书）
- 按"来源→目标"配对，能区分"广告跳转"（从短剧发起）和"你手动点开"（从桌面发起）。

## 功能
- 无障碍服务**事件驱动**实时侦测前台应用（不轮询、无 ROM 延迟）
- 冻结/解冻目标应用（借 Shizuku 执行 `pm disable-user` / `pm enable`）
- 一键暂停（打游戏时）与通知栏"暂停 / 继续"按钮
- 应用列表可搜索，可切换"列出系统应用"
- 来源/目标多选配置，延迟解冻、防穿兜底

## 使用步骤（一次性配置）

1. **装 Shizuku**（官方渠道）：安装后，用电脑 `adb` 连接手机执行 Shizuku 显示的激活命令。
2. **授权 NoJump**：在 Shizuku 的授权管理里允许 NoJump。
3. **开启无障碍服务**：设置 → 无障碍 → 找到 **NoJump** → 开启（或从 NoJump 主界面点【无障碍服务】直达）。这是实时侦测前台所必需的。
4. 打开 NoJump：勾选 **来源**（短剧）与 **目标**（美团等）。
5. 点【启动拦截后台服务】。

## 重要边界：关于"暂停"与防封
- 一键暂停：立即可**停止 NoJump 的一切冻结/解冻调用**，并解冻已冻结应用（功能层面 100% 有效）。
- Shizuku 后台**服务进程**属于系统权限，普通 App 无法停止；若某个反作弊会扫描"设备上是否有 Shizuku 进程"，需到 **Shizuku App 里停止服务**（通知栏"去停 Shizuku"可跳转引导）。
- 本项目不注入、不篡改游戏，只对目标应用执行冻结；但大厂反作弊对高权限工具无"不封"承诺，谨慎使用。

## 技术栈
| 项 | 选择 |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material3（BOM 2026.02.01）|
| 构建 | AGP 9.3.0 · Kotlin DSL |
| 版本 | minSdk 26 / target 37 |
| 持久化 | SharedPreferences（`object` 单例）|
| 权限方案 | Shizuku（`dev.rikka.shizuku:api` / `provider` 12.1.0）+ 无障碍服务 |

## 项目结构
```
app/src/main/java/com/example/nojump/
├── MainActivity.kt                  # Compose 主界面 + 权限引导
├── BlockService.kt                  # 前台服务：驱动状态机 + 通知栏暂停/继续
├── ForegroundAccessibilityService.kt# 无障碍服务：事件驱动侦测前台
├── RuleEngine.kt                    # 来源→目标判定 + 延迟解冻
├── ForegroundWatcher.kt             # 前台读取入口（无障碍主判 + 兜底）
├── Freezer.kt                       # 冻结/解冻执行
├── ShizukuManager.kt                # Shizuku 借权封装
├── RuleStore.kt                     # 规则/状态持久化
└── AppList.kt                       # 已装应用列表
```

## 构建
```bash
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```