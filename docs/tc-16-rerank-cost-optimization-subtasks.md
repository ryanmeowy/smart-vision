# TC-16 子任务清单：Rerank 成本优化（窗口化 + 融合打分）

更新时间：2026-04-10

## 1. 方案目标

- 降低 rerank 链路时延与调用成本（重点关注 P95）。
- 保持相关性不回退（Recall@K / nDCG@K）。
- 与已上线分页能力兼容，避免跨页排序抖动。

## 2. 子任务拆分（按执行顺序）

| 序号 | 子任务 | 优先级 | 状态 | 主要产出 | 依赖 | 预估 |
|---|---|---|---|---|---|---|
| ST-01 | 明确优化基线与目标 | P0 | 已完成 | 固化 baseline 口径（P95、Recall@K、nDCG@K、rerank 请求文档数）与目标阈值 | 无 | 0.5 天 |
| ST-02 | 新增 rerank 窗口化配置 | P0 | 已完成 | 新增 `window-enabled/window-size/window-factor/window-min/window-max` 配置项与默认值 | ST-01 | 0.5 天 |
| ST-03 | 实现窗口化 rerank 逻辑 | P0 | 已完成 | 仅对窗口候选执行 rerank，非窗口候选保留原检索顺序与分数 | ST-02 | 0.5-1 天 |
| ST-04 | 实现融合打分策略 | P0 | 已完成 | 使用 `finalScore = alpha * retrieval + beta * rerank` 进行窗口内排序（参数可配置） | ST-03 | 0.5-1 天 |
| ST-05 | 稳定排序与分页兼容校验 | P0 | 已完成 | 同分 tie-break：`fusion DESC -> retrieval DESC -> id ASC -> originIndex ASC`，顺序稳定 | ST-04 | 0.5 天 |
| ST-06 | 监控与观测补齐 | P1 | 已完成 | 指标补齐：窗口大小/窗口占比/窗口命中、rerank 延迟、降级计数 | ST-03~05 | 0.5 天 |
| ST-07 | 测试与回归 | P0 | 已完成 | 新增窗口/融合/降级/tie-break 单测，关键链路回归通过 | ST-03~06 | 0.5-1 天 |
| ST-08 | 灰度发布与参数调优 | P0 | 已完成（发布手册） | 灰度步骤、参数矩阵、回滚策略与观察清单 | ST-07 | 0.5 天 |

## 3. 推荐默认参数（V1）

- `window-enabled: true`
- `window-factor: 3`
- `window-min: 20`
- `window-max: 80`
- `window-size: 40`（当未显式传入时作为兜底）
- `fusion-alpha: 0.6`
- `fusion-beta: 0.4`

说明：以上参数是冷启动默认值，最终以 `TC-13` 离线评测结果与线上灰度数据为准。

## 3.1 ST-01 基线与目标（已固化）

- 基线采集口径：
- `P95`: `smartvision.search.rerank.latency`（Timer）
- `rerank 请求文档数`: `smartvision.search.rerank.window.size`（DistributionSummary）
- `窗口占比`: `smartvision.search.rerank.window.ratio`（DistributionSummary）
- `降级次数`: `smartvision.search.rerank.fallback`（Counter）
- `Recall@K / nDCG@K`: 通过 `TC-13` 离线评测脚本按版本对比

- 目标阈值：
- P95 相比 baseline 下降 `>= 20%`
- 平均窗口占比（window/candidates）`<= 0.5`
- rerank 降级率（fallback/calls）`< 5%`
- Recall@K 回退 `<= 1%`，nDCG@K 回退 `<= 2%`

## 4. 影响面分析

- 主要影响：`HybridRetrievalStrategy` 的 rerank 逻辑和排序输出。
- 配置影响：`application.yaml` 增加窗口与融合参数。
- 观测影响：新增指标与日志字段，支持灰度对比。
- 兼容性：不改变外部 API 契约；分页接口可复用首查排序结果。

## 5. 边界说明

- 边界内：窗口化 rerank、融合打分、参数配置化、监控与回归、灰度调优。
- 边界外：更换 rerank 模型、重构检索召回链路、全自动 MLOps 平台化。

## 6. DoD（验收标准）

1. 与 baseline 对比，P95 下降（目标 20%+）。
2. Recall@K / nDCG@K 不明显回退（阈值在 ST-01 固化）。
3. 分页连续翻页顺序稳定，无重复无漏项。
4. rerank 异常时可自动降级并有可观测日志/指标。
5. 配置可动态调整，测试与回归通过。

## 7. 执行记录（2026-04-10）

- ST-02 配置已落地：`application.yaml` 新增窗口与融合参数。
- ST-03/04/05 已落地：`HybridRetrievalStrategy` 支持窗口化 rerank、融合打分排序、稳定 tie-break。
- ST-06 指标已落地：
- `smartvision.search.rerank.calls`
- `smartvision.search.rerank.latency`
- `smartvision.search.rerank.window.size`
- `smartvision.search.rerank.window.ratio`
- `smartvision.search.rerank.window.hit`
- `smartvision.search.rerank.fallback`
- ST-07 测试已补齐：新增 `HybridRetrievalStrategyTest`，覆盖窗口、融合、降级、同分排序。
- ST-08 已产出发布手册（见下）。

## 8. ST-08 灰度发布与调参手册

- 灰度顺序：
1. 5% 流量：开启 `window-enabled=true`，默认参数不变。
2. 20% 流量：观察 24 小时（延迟、降级率、质量抽检）。
3. 50% 流量：按指标对 `window-size/window-max/alpha/beta` 做小步调参。
4. 100% 流量：指标满足阈值后全量。

- 推荐调参矩阵：
1. 延迟优先：`window-size=30`，`window-max=60`，`alpha=0.7`，`beta=0.3`
2. 质量优先：`window-size=50`，`window-max=100`，`alpha=0.5`，`beta=0.5`
3. 均衡默认：`window-size=40`，`window-max=80`，`alpha=0.6`，`beta=0.4`

- 回滚策略：
1. 快速回滚：`app.search.rerank.window-enabled=false`（恢复全量 rerank）
2. 保守回滚：`fusion-alpha=1.0`、`fusion-beta=0.0`（仅保留检索分）
3. 降级保障：当 rerank 返回空结果时自动 fallback 到原排序
