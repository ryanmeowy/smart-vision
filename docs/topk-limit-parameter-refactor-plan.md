# topK / limit 参数语义收敛改造方案（后端）

## 1. 背景与问题

当前检索链路中，`topK` 与 `limit` 在不同层被重复放大和混用，导致前端参数控制不直观，具体表现为：

1. 分页入口会改写 `topK`，使其不再等于前端传入值。
2. Hybrid 策略会将 `topK` 与 `limit` 同时扩展为 `recallSize`。
3. 前端策略切换后，参数含义与实际执行语义存在偏差。

这会造成“前端传了参数但控制失效”的认知问题，也增加联调成本。

## 2. 目标语义（统一约定）

统一参数职责如下：

1. `limit`：最终返回结果上限（分页场景下即每页大小）。
2. `topK`：向量子查询召回规模（KNN `k`），仅对包含向量召回的策略生效。

策略语义：

1. `HYBRID`：`topK` 与 `limit` 都生效；允许 `topK > limit`（先多召回，后融合/重排/截断）。
2. `VECTOR_ONLY`：`topK` 与 `limit` 语义对齐，默认 `topK = limit`。
3. `TEXT_ONLY`：`topK` 不生效，仅 `limit` 生效。

## 3. 非目标（本次不做）

1. 不重构 RRF、Cross-Encoder 的核心算法。
2. 不修改分页会话机制（首查建会话、后续游标切片）。
3. 不引入新的检索策略类型。

## 4. 详细改造项

## 4.1 应用层：分页首查不再改写 topK

涉及文件：

1. `src/main/java/com/smart/vision/core/search/application/impl/SmartSearchServiceImpl.java`

改造点：

1. `toInitialSearchQuery(...)` 保留前端 `topK`（仅做安全边界 clamp）。
2. `limit` 仍用于分页窗口大小计算与切片。
3. 禁止通过 `windowSize` 反向抬高 `topK`。

预期行为：

1. 分页首查时，前端 `topK` 语义保持稳定。
2. 分页能力继续由 `limit/pageSize` 控制，不破坏现有游标协议。

## 4.2 策略层：Hybrid 分离“召回规模”与“返回规模”

涉及文件：

1. `src/main/java/com/smart/vision/core/search/domain/strategy/impl/HybridRetrievalStrategy.java`
2. `src/main/java/com/smart/vision/core/search/domain/model/HybridSearchParamDTO.java`（如需新增字段）
3. `src/main/java/com/smart/vision/core/search/infrastructure/persistence/es/query/spec/HybridQuerySpec.java`

改造点：

1. `requestTopK` 仅用于向量子查询规模（或衍生内部 `vectorRecallK`）。
2. `requestLimit` 仅用于最终返回上限。
3. 如需 RRF 候选扩展，使用内部变量（例如 `fusionRecallSize`），不得覆盖请求字段语义。
4. ES 请求构建中：
   - `knn.k` 使用 `topK`（或内部向量召回规模）。
   - `size` 使用候选窗口或返回上限时，需明确“用于候选阶段”还是“最终响应阶段”。
5. `applyCrossEncoderRerank(..., requestLimit, ...)` 继续作为最终截断出口，保证 `finalCount <= limit`。

说明：

1. Hybrid 内部可继续做候选放大，但要与 API 字段解耦，避免“用户参数被悄悄改写”。

## 4.3 其他策略：显式化参数规则

涉及文件：

1. `src/main/java/com/smart/vision/core/search/domain/strategy/impl/VectorOnlyRetrievalStrategy.java`
2. `src/main/java/com/smart/vision/core/search/domain/strategy/impl/TextOnlyRetrievalStrategy.java`

改造点：

1. `VECTOR_ONLY`：后端兜底 `topK = limit`（当前端未联动或请求异常时保持一致）。
2. `TEXT_ONLY`：忽略 `topK`，仅使用 `limit`。

## 4.4 接口契约与文档

涉及文件：

1. `docs/openapi-ui-draft.yaml`
2. DTO 注释与接口注释（`SearchQueryDTO`、`SearchPageQueryDTO`、`SmartSearchService`）

改造点：

1. 明确写出参数语义与策略差异（Hybrid/Vector/Text）。
2. 明确文本策略下 `topK` 不生效。
3. 示例请求中体现：
   - Hybrid：`topK > limit`
   - Vector：`topK = limit`
   - Text：可不传 `topK`

## 4.5 前端联动约束（先文档约定）

涉及文件（后续实现时）：

1. `ui_streamlit/pages.py`

约束规则：

1. `HYBRID`：`topK`、`limit` 均可编辑，默认建议 `topK > limit`。
2. `VECTOR_ONLY`：`limit` 置灰，界面仅允许编辑 `topK`；请求中由前端将 `limit` 与 `topK` 对齐（`limit = topK`）。
3. `TEXT_ONLY`：`topK` 置灰或隐藏，仅允许编辑 `limit`。

说明：

1. 该规则用于确保用户在 UI 层的心智模型与后端参数语义一致。

## 5. 兼容性与影响面

## 5.1 行为变化

1. Hybrid 在同一请求参数下，候选集规模与最终排序可能变化。
2. 分页首查的候选数量不再因 `windowSize` 强制抬高 `topK`。

## 5.2 联调影响

1. 前端策略化控件（Hybrid/Vector/Text）与后端语义将一致。
2. 原先依赖“后端自动放大 topK”的调用方，可能观察到结果分布变化。

## 5.3 性能影响

1. 在未显式放大 `topK` 的请求下，Hybrid 召回规模可能下降，时延可能下降、召回率可能波动。
2. 需通过可配置内部候选参数平衡质量与成本，而不是改写用户入参。

## 6. 测试方案

## 6.1 单元测试

新增/修改测试建议：

1. `SmartSearchServiceImplPageTest`
   - 断言 `toInitialSearchQuery` 不再放大 `topK`。
2. `HybridRetrievalStrategy` 相关测试
   - 断言 `limit` 仅控制最终返回上限。
   - 断言 `topK` 仅影响向量召回规模。
3. `VectorOnlyRetrievalStrategy` 测试
   - 断言 `topK` 与 `limit` 一致性兜底。
4. `TextOnlyRetrievalStrategy` 测试
   - 断言忽略 `topK`。

## 6.2 集成回归

1. `/search` 三种策略参数行为回归。
2. `/search-page` 首查与翻页一致性回归（无重复、无漏项、排序稳定）。
3. OCR 开关组合回归（Hybrid/Text）。

## 7. 发布与灰度建议

1. 增加阶段性开关（如 `app.search.param-semantics-v2.enabled`），支持灰度。
2. 先在测试环境联调前端策略控件，再灰度到低流量环境。
3. 观测指标：
   - 请求 P95 时延
   - 结果条数分布
   - 首屏点击率/满意度（如有埋点）
4. 若质量回退，优先回调内部候选参数，不回退 API 语义。

## 8. 开发顺序建议

1. 先改应用层与策略层参数传递（最小闭环）。
2. 再补单测与集成测试。
3. 最后更新 OpenAPI 与前端联调说明。

---

本方案核心原则：**不再改写用户输入参数语义；内部优化使用独立候选参数实现。**
