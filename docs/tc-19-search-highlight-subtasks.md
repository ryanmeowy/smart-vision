# TC-19 子任务清单：搜索高亮片段（ES 证据高亮）

更新时间：2026-04-08

## 1. 现状与目标

- 现状：Text Search 页面已有前端关键词高亮（本地字符串匹配）。
- 目标：由 Elasticsearch 返回命中片段（highlight fragment），前端优先展示后端片段，提升可解释性与一致性。

## 2. 子任务拆分（按执行顺序）

| 序号 | 子任务 | 优先级 | 状态 | 主要产出 | 依赖 | 预估 |
|---|---|---|---|---|---|---|
| ST-01 | 明确高亮字段与规则 | P0 | 已完成 | 规则固定：字段 `ocrContent/tags/fileName`；优先级 `ocrContent > tags > fileName`；OCR `fragmentSize=160`、`numberOfFragments=1`；fallback 保留前端关键词高亮 | 无 | 0.5 天 |
| ST-02 | ES 查询增加 highlight 配置 | P0 | 已完成 | 在 Text/Hybrid 查询中加入 highlight（`pre_tags=<em>`、`post_tags=</em>`、`requireFieldMatch=false`） | ST-01 | 0.5 天 |
| ST-03 | 结果转换层透传高亮片段 | P0 | 已完成 | 从 ES `Hit.highlight` 读取片段并映射到 `SearchResultDTO.highlight`（含 RRF 保留） | ST-02 | 0.5 天 |
| ST-04 | UI 渲染策略升级 | P0 | 已完成 | 优先渲染后端 `highlight`；缺失时回退当前前端关键词高亮；对 `<em>/<strong>` 安全转换为 `<mark>` | ST-03 | 0.5 天 |
| ST-05 | 文档与契约更新 | P1 | 已完成 | OpenAPI 同步 `highlight` 语义与降级规则 | ST-03 | 0.5 天 |
| ST-06 | 测试与回归验证 | P0 | 已完成 | 覆盖 QuerySpec 高亮配置、转换层优先级、装配层映射；核心回归通过 | ST-04~05 | 0.5-1 天 |

## 3. 影响面分析

- 后端查询层：需要扩展 ES 查询构建，增加 highlight 配置。
- 后端转换层：需要在搜索结果转换时读取并透传高亮片段。
- 前端展示层：需要调整渲染优先级，不改变交互流程。
- 测试与文档：需补充契约与回归用例，避免体验倒退。

## 4. 边界说明

- 边界内：ES 命中片段高亮、后端透传、前端优先展示、缺失平滑降级。
- 边界外：复杂语义摘要生成、多语言 analyzer 大改、同义词体系重构。

## 5. DoD（验收标准）

1. 典型文本查询可返回非空 `highlight` 片段（命中时）。
2. `highlight` 为空或缺失时，页面仍可用且保留当前关键词高亮体验。
3. 不影响现有分页加载与排序稳定性。
4. 新增/调整测试通过，核心回归无异常。
