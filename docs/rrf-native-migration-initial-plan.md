# RRF 原生化改造初步方案（约束版）

## 1. 目标

在不破坏现有应用层 RRF 路径的前提下，引入 ES8 原生 `retriever.rrf` 路径，并支持一键切换。

## 2. 强约束（必须满足）

1. **现有 RRF 实现不能改语义**  
   现有 `HybridRetrievalStrategy + RrfFusionService` 路径保持原样，作为稳定基线。

2. **现有指标不能改口径**  
   已有指标名、tag 结构、统计含义不变（例如 `smartvision.search.rerank.*`、`smartvision.strategy.*`）。

3. **必须有切换开关，支持一键切换**  
   在配置层通过单一布尔开关切换“当前 RRF”与“原生 RRF”。

4. **原生 RRF 允许语义差异**  
   原生路径不要求沿用“第一路候选白名单”语义，可使用 ES 原生融合语义。

5. **原生 RRF 不要求支持顶层 sort**  
   顶层 `sort` 的限制可接受，不作为阻塞条件。

## 3. 当前路径（保留不动）

1. 入口：`HybridRetrievalStrategy#search(...)`
2. 候选：`hybridSearch + vectorSearch + textSearch`
3. 融合：`RrfFusionService#fuse(...)`
4. 精排：Cross-Encoder rerank

关键代码：

1. `src/main/java/com/smart/vision/core/search/domain/strategy/impl/HybridRetrievalStrategy.java`
2. `src/main/java/com/smart/vision/core/search/domain/ranking/RrfFusionService.java`
3. `src/main/java/com/smart/vision/core/search/infrastructure/persistence/es/query/spec/HybridQuerySpec.java`

## 4. 目标架构（双路径并存）

## 4.1 配置开关

建议新增：

```yaml
app:
  search:
    rrf:
      native-enabled: false
```

说明：

1. `false`：走当前应用层 RRF（默认）。
2. `true`：走 ES 原生 `retriever.rrf`。

## 4.2 路径路由

在 `HybridRetrievalStrategy` 内按开关分支：

1. `native-enabled=false`：调用现有 `applyRrfFusion(...)`（不改）。
2. `native-enabled=true`：走新增 `nativeRrfSearch(...)`（新路径）。

## 4.3 原生 RRF 查询构建

新增专用查询构建（建议新增 spec/factory 方法，不替换旧实现）：

1. `retriever.rrf.retrievers` 包含：
   - `standard`（keyword + graph）
   - `knn`（向量）
2. 使用 `rank_constant`、`rank_window_size` 对齐现有配置。
3. 保留 highlight 输出（按 ES 支持能力）。

## 5. 改动清单（初步）

## 5.1 代码改动

1. `HybridRetrievalStrategy`  
   - 新增开关注入与分支路由。  
   - 保留现有分支代码不修改行为。

2. `SearchRequestFactory`  
   - 新增 `buildHybridNativeRrf(...)`（命名可调整）。  
   - 旧 `buildHybrid(...)` 保持不动。

3. `query/spec`  
   - 新增原生 RRF QuerySpec（建议新文件，避免污染旧 spec）。

4. `ImageRepositoryCustom/ImageRepositoryImpl`  
   - 新增 native RRF 查询入口方法。  
   - 旧 `hybridSearch(...)` 等接口保留。

## 5.2 配置改动

1. `application.yaml` 增加 `app.search.rrf.native-enabled` 默认值。
2. 现有 `app.search.rrf.rank-constant/candidate-multiplier/max-candidates` 保留。

## 5.3 测试改动

1. 新增 native 分支单测（开关 true/false 两条路径）。
2. 现有 `RrfFusionServiceTest`、`HybridRetrievalStrategyTest` 原断言尽量保持。
3. 新增 native 路径的契约测试（排序、高亮存在性、结果数）。

## 6. 影响面评估

## 6.1 对当前链路

1. 默认开关为 `false` 时无行为变化。
2. 当前 API 契约、前端逻辑无需强制调整。

## 6.2 对 native 链路

1. 结果语义可与当前实现不同（明确接受）。
2. 可能出现“向量召回文档无文本高亮”的场景（可接受，需在评估报告中说明）。
3. 顶层 `sort` 限制不作为阻塞项。

## 6.3 对观测

1. 现有指标必须保持不变。
2. 可新增“native 专属”指标，但不得替换或改写旧指标口径。
3. 建议新增维度标签 `path=legacy|native` 到新增指标，不改旧指标。

## 7. 发布与回滚

1. 默认 `native-enabled=false` 发布。
2. 灰度时仅切换该开关即可一键启停原生路径。
3. 异常回滚：立即设回 `false`，恢复现有应用层 RRF。

## 8. 里程碑与工期（初步）

1. M1（0.5 天）：开关、路由骨架、配置落地。
2. M2（1-2 天）：原生 RRF QuerySpec + Repository 接口打通。
3. M3（0.5-1 天）：单测与回归、文档补充。

总计：约 `2-3.5` 天（不含线上灰度观察窗口）。

---

结论：

1. 在你设定的约束下，推荐采用“双路径并存 + 配置开关切换”。
2. 默认保持现有实现与指标完全不动。
3. 原生 RRF 作为可切换实验路径逐步验证，风险可控、回滚简单。
