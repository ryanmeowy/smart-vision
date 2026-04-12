# TC-22 子任务清单：ES 正式 CLI 迁移（Picocli）

更新时间：2026-04-10

## 1. 目标

- 将 `scripts/sv-es` 迁移为正式 CLI，可版本化分发与稳定维护。

## 2. 子任务拆分（按执行顺序）

| 序号 | 子任务 | 优先级 | 状态 | 主要产出 | 依赖 | 预估 |
|---|---|---|---|---|---|---|
| ST-01 | 冻结命令契约与兼容清单 | P0 | 已完成 | 命令、参数、返回展示与错误提示兼容基线 | 无 | 0.5 天 |
| ST-02 | 搭建 CLI 工程骨架 | P0 | 已完成 | Picocli 主命令、子命令模块、配置加载框架 | ST-01 | 0.5 天 |
| ST-03 | 实现会话命令 | P0 | 已完成 | `login/logout/whoami` + profile 文件读写 | ST-02 | 0.5 天 |
| ST-04 | 实现 cluster/index 查询命令 | P0 | 已完成 | `cluster health/stats`、`index list/stats`、`mapping get` | ST-02 | 0.5-1 天 |
| ST-05 | 实现 doc 查询命令 | P0 | 已完成 | `doc get/search` 参数与请求封装 | ST-03~04 | 0.5-1 天 |
| ST-06 | 实现输出渲染层 | P0 | 已完成 | `json/table` 输出，与脚本行为对齐 | ST-04~05 | 0.5 天 |
| ST-07 | 错误码映射与退出码策略 | P0 | 已完成 | 400/401/403/404/500 友好提示 + 非0退出 | ST-04~06 | 0.5 天 |
| ST-08 | 打包与分发脚本 | P1 | 已完成 | fat jar 打包、启动脚本、版本号注入 | ST-07 | 0.5 天 |
| ST-09 | 测试与回归 | P0 | 已完成 | 单测 + 命令级冒烟 + 与脚本版对照回归 | ST-03~08 | 1 天 |
| ST-10 | 发布文档与迁移说明 | P1 | 已完成 | 安装/使用/升级/回滚文档，脚本到正式 CLI 迁移指南 | ST-09 | 0.5 天 |

## 3. 里程碑建议

1. M1：完成 ST-01~ST-03，具备登录态能力。
2. M2：完成 ST-04~ST-06，核心查询命令可用。
3. M3：完成 ST-07~ST-08，可打包试用。
4. M4：完成 ST-09~ST-10，准备团队试运行。

## 4. 风险与对策

1. 风险：命令行为偏离脚本版。
- 对策：维护命令对照表与契约回归测试。

2. 风险：打包产物跨环境可用性问题。
- 对策：先 jar 后 native，分阶段推进。

3. 风险：切换期用户混用导致认知成本增加。
- 对策：保留脚本版过渡期，并在文档中明确推荐路径。

## 5. DoD（验收标准）

1. 正式 CLI 可覆盖脚本版当前全部核心命令。
2. 登录后无需重复传 token。
3. 常见错误能给出清晰可执行提示。
4. 打包产物可在目标环境直接运行。
5. 发布文档、迁移文档与回归记录齐备。

## 6. 执行记录（2026-04-10）

- 已创建独立工程：`cli/escli`（Picocli + Jackson + Maven Shade）。
- 已实现命令：
- `login/logout/whoami`
- `cluster health/stats`
- `index list/stats`
- `mapping get`
- `doc get/search`
- 已实现输出与错误策略：
- `json/table` 双格式
- 非 200 响应输出错误提示与排障 hint，返回非 0 退出码
- 已实现打包与运行入口：
- 产物：`cli/escli/target/sv-es-cli.jar`
- 启动脚本：`cli/escli/bin/sv-es`
- 已补充测试：
- `ConfigStoreTest`
- `ErrorHintsTest`
- 已补充文档：
- `docs/es-cli-official-quickstart.md`
- `docs/es-cli-official-migration-guide.md`
