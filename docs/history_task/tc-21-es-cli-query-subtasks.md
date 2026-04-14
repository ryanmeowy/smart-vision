# TC-21 子任务清单：ES 查询 CLI（后端封装 + 命令简化）

更新时间：2026-04-10

## 1. 方案目标

- 将高频 ES 查询场景从原生 `curl` 迁移到统一 CLI。
- 通过后端白名单 API 控制查询能力，统一鉴权、审计、风控。
- 降低排查与运维门槛，提升团队查询效率与一致性。

## 2. 子任务拆分（按执行顺序）

| 序号 | 子任务 | 优先级 | 状态 | 主要产出 | 依赖 | 预估 |
|---|---|---|---|---|---|---|
| ST-01 | 冻结 MVP 命令与 API 字段 | P0 | 已完成 | 固化 7 个命令、参数与响应字段；确定 out-of-scope（写操作/全 DSL） | 无 | 0.5 天 |
| ST-02 | 设计查询白名单与参数校验规则 | P0 | 已完成 | 已完成索引名/索引模式/page-size 校验，以及 `doc search` 的字段/排序/query 字符白名单 | ST-01 | 0.5 天 |
| ST-03 | 实现 cluster 类 API | P0 | 已完成 | `GET /api/es/cluster/health`、`GET /api/es/cluster/stats` | ST-01 | 0.5 天 |
| ST-04 | 实现 index 类 API | P0 | 已完成 | `GET /api/es/indices`、`GET /api/es/indices/{index}/stats`、`GET /api/es/indices/{index}/mapping` | ST-01 | 1 天 |
| ST-05 | 实现 doc 类 API | P0 | 已完成 | `GET /api/es/indices/{index}/docs/{id}`、`POST /api/es/indices/{index}/search` | ST-02 | 1 天 |
| ST-06 | 接入鉴权与索引级访问控制 | P0 | 已完成 | `/api/es/**` 已接入 `@RequireAuth`；索引访问受 `allowed-index-patterns` + 向量索引默认白名单控制 | ST-03~05 | 0.5 天 |
| ST-07 | 接入审计日志与可观测指标 | P1 | 已完成 | 新增审计日志（caller/command/outcome/details）和指标 `smartvision.escli.requests`、`smartvision.escli.latency` | ST-03~06 | 0.5 天 |
| ST-08 | 实现 CLI 命令骨架与配置加载 | P0 | 已完成 | `scripts/sv-es` 子命令路由完成，支持 `--profile/--base-url/--token` 配置加载 | ST-01 | 0.5 天 |
| ST-09 | 实现 CLI 命令与输出格式化 | P0 | 已完成 | 7 个命令可调用后端 API；支持 `json/table` 输出（`jq` 可用时表格化） | ST-08、ST-03~05 | 1 天 |
| ST-10 | 完成错误码映射与友好提示 | P1 | 已完成 | CLI 对 400/401/403/404/500 提供友好提示与排障建议，输出 `requestId/errorId` | ST-09 | 0.5 天 |
| ST-11 | 测试与回归（后端+CLI） | P0 | 已完成 | 已补 controller + domain + service 测试，CLI 语法/登录链路冒烟通过 | ST-03~10 | 1 天 |
| ST-12 | 发布文档与试运行 | P1 | 已完成 | 已补 quickstart 与试运行手册（发布检查、灰度步骤、回滚与排障） | ST-11 | 0.5 天 |

## 3. 里程碑建议

1. M1（第 3 天）：完成 ST-01 ~ ST-03，可查询集群状态。
2. M2（第 6 天）：完成 ST-04 ~ ST-06，后端查询能力闭环。
3. M3（第 8 天）：完成 ST-08 ~ ST-10，CLI 主能力可用。
4. M4（第 10 天）：完成 ST-11 ~ ST-12，进入试运行。

## 4. 影响面分析

- 后端：新增 ES 查询聚合层、参数校验、鉴权与审计。
- CLI：新增命令入口、参数解析、输出渲染与错误处理。
- 运维：新增指标与日志检索维度，便于定位查询问题。

## 5. 风险与对策

1. 风险：查询语法约束过严，导致可用性下降。
- 对策：先覆盖高频查询语法，逐步扩展白名单。

2. 风险：CLI 与后端契约漂移。
- 对策：固定 OpenAPI/契约测试，发布前执行端到端校验。

3. 风险：大查询导致 ES 压力上升。
- 对策：默认限流、限制 `size`、统一超时与慢查询告警。

## 6. DoD（验收标准）

1. 7 个 MVP 命令全部可用，支持 `table/json` 两种输出。
2. 查询全链路经过后端鉴权与审计，不暴露 ES 直连凭证。
3. 错误码稳定且 CLI 提示可读，支持 `requestId` 追踪。
4. 常见排查场景可在 1 分钟内完成，不再依赖手写 `curl`。
5. 提供最少 1 份使用文档与 1 套回归测试清单。

## 7. 任务负责人建议（可选）

- 后端 owner：ST-02 ~ ST-07
- CLI owner：ST-08 ~ ST-10
- QA/联调 owner：ST-11 ~ ST-12

## 8. 执行记录（2026-04-10）

- 已落地 `search/escli` 子模块骨架（controller + service + dto + validator）。
- 已实现并编译通过：
- `GET /api/es/cluster/health`
- `GET /api/es/cluster/stats`
- `GET /api/es/indices`
- `GET /api/es/indices/{index}/stats`
- `GET /api/es/indices/{index}/mapping`
- `GET /api/es/indices/{index}/docs/{id}`
- `POST /api/es/indices/{index}/search`
- 已接入鉴权与索引访问控制：
- `/api/es/**` 添加 `@RequireAuth`
- 新增 `app.search.escli.allowed-index-patterns`（为空时回退到向量索引别名/物理索引白名单）
- 已接入审计与指标：
- 审计日志：`escli_audit`（success/failure、caller 指纹、command、details/reason）
- 指标：`smartvision.escli.requests`（按 endpoint/outcome 计数）、`smartvision.escli.latency`（按 endpoint 计时）
- 已落地 CLI 脚本：
- `scripts/sv-es`（覆盖 7 个 MVP 命令，支持 `--profile --base-url --token --format`）
- 已补充 CLI 使用文档：
- `docs/es-cli-quickstart.md`
- 已补充试运行/发布手册：
- `docs/es-cli-trial-runbook.md`
- 已补充错误码友好提示：
- CLI 对 400/401/403/404/500 返回统一可读 hint，并在存在时打印 `requestId/errorId`
- 已补充参数校验单测：
- `EsCliIndexValidatorTest`
- `EsCliQueryServiceTest`（校验前置分支与 doc-search 白名单分支）
- `EsCliAccessControlTest`（白名单与默认回退策略）
- `EsCliControllerTest`（接口返回与参数校验分支）
