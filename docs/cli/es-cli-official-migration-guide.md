# ES CLI 迁移指南（脚本版 -> 正式版）

更新时间：2026-04-10

## 1. 迁移结论

- 推荐逐步切换到正式 CLI：`cli/escli/bin/sv-es`。
- 脚本版 `scripts/sv-es` 保留一个过渡周期用于兜底。

## 2. 命令兼容性

以下命令保持兼容：

1. `login/logout/whoami`
2. `cluster health/stats`
3. `index list/stats`
4. `mapping get`
5. `doc get/search`

## 3. 差异说明

1. 正式 CLI 通过 Java 打包产物运行（`sv-es-cli.jar`）。
2. 错误处理与退出码更稳定，便于 CI 脚本集成。
3. 分发与版本管理更规范（可做版本号和发布流程）。

## 4. 推荐切换步骤

1. 构建正式 CLI：`cd cli/escli && /opt/homebrew/bin/mvn -q test package`
2. 使用同一 profile 执行 `login`
3. 运行高频命令做对照验证（cluster/index/doc）
4. 团队文档默认入口切换为正式 CLI

## 5. 回滚策略

1. 如遇阻塞，临时回退到 `scripts/sv-es`。
2. 保留相同后端接口，不影响服务端。
3. 修复后继续推进正式 CLI。
