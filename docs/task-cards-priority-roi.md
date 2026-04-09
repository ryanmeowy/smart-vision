# SmartVision 任务卡清单（按优先级与 ROI 排序）

更新时间：2026-04-08

| 排名 | 任务卡 | 优先级 | ROI | 目标 | In Scope（边界内） | Out Scope（边界外） | 验收标准（DoD） | 预计投入 |
|---|---|---|---:|---|---|---|---|---|
| 1 | `TC-11 安全链路升级（Token + STS）` | P0 | 10/10 | 降低安全风险、提升可审计性 | `refresh-token` 改 `POST`；上传令牌改为带过期与 scope；STS 改服务端安全下发；限流与审计日志 | 完整 OAuth2 / 多租户 RBAC | 非法请求必拒绝；UI 不再输入加密 key/iv；关键路径单测/集成测试通过 | 2-3 天 |
| 2 | `TC-12 搜索分页闭环（searchAfter）` | P0 | 10/10 | 解决结果“只能看一屏”问题 | 后端透出 `nextSearchAfter`；策略层透传游标；前端“加载更多”与状态管理 | 复杂无限滚动体验优化 | 连续翻 3-5 页无重复/漏项；排序稳定 | 1.5-2 天 |
| 3 | `TC-13 离线评测基线` | P0 | 9.5/10 | 建立“优化可量化”体系 | 构建评测样本；脚本产出 Recall@K / nDCG@K / P95；保存 baseline 报告 | 全自动 MLOps 平台 | 一条命令复现实验；报告可追溯版本与配置 | 1-1.5 天 |
| 4 | `TC-14 检索质量门控恢复` | P0 | 9/10 | 降低低质尾部结果 | 启用并策略化 `similarFilter`；阈值配置化；加回归测试 | 在线学习排序 | 低分尾部明显收敛；核心查询召回不回退 | 1 天 |
| 5 | `TC-15 批任务可运营化（失败分层+持久化）` | P0 | 8.5/10 | 提升排障与重试效率 | 错误码分层（OCR/Embedding/ES/OSS）；任务状态落盘（不只 Redis TTL）；UI 显示重试建议 | MQ/工作流引擎重构 | 失败项可分类统计；24h 后任务仍可追踪 | 2 天 |
| 6 | `TC-16 Rerank 成本优化（窗口化+融合）` | P1 | 8.5/10 | 控制延迟与模型成本 | 仅 rerank TopN 候选；融合分替代硬覆盖；参数配置化 | 模型替换 | P95 下降（目标 20%+）；相关性不劣化 | 1-1.5 天 |
| 7 | `TC-17 查询图谱解析缓存与降级` | P1 | 8/10 | 稳定混合检索时延 | 关键词->三元组缓存；超时熔断；失败回退空图谱 | 图谱服务重构 | 图谱服务异常时主链路可用；命中缓存时延显著下降 | 1 天 |
| 8 | `TC-18 临时文件生命周期治理` | P1 | 7.5/10 | 降低 OSS 成本与脏数据 | 明确开启调度或改事件后删；补监控告警；清理策略配置化 | 全量对象治理平台 | `temp/` 历史堆积可控；清理任务可观测 | 0.5-1 天 |
| 9 | `TC-19 搜索高亮片段（已完成）` | P1 | 7/10 | 提升结果可解释性与命中证据可读性 | ES 查询增加 highlight（`ocrContent/tags/fileName`）；后端返回 `highlight` 片段；UI 优先展示后端片段，缺失时回退前端关键词高亮；补充测试与文档 | 复杂语义解释系统；多语言分词与同义词体系改造；复杂摘要生成 | 用户可看到 ES 命中片段；`highlight` 为空时页面平滑降级；核心查询回归通过 | 1-2 天 |
| 10 | `TC-20 筛选/排序能力` | P2 | 6.5/10 | 强化产品可用性 | 增加标签/时间/阈值筛选与排序参数；查询层 filter 支持 | 复杂推荐排序策略 | 常用筛选组合可用；接口文档与测试齐全 | 2 天 |

## 推荐执行顺序（第一阶段）

建议优先并行推进：`TC-11`、`TC-12`、`TC-13`、`TC-14`。  
这 4 张卡可以最快建立安全性、可用性、可量化评估与检索效果的基础盘。

## 子任务卡索引（2026-04-08）

- TC-12： [tc-12-search-pagination-subtasks.md](/Users/ryan/personal/smart-vision/docs/tc-12-search-pagination-subtasks.md)
- TC-16： [tc-16-rerank-cost-optimization-subtasks.md](/Users/ryan/personal/smart-vision/docs/tc-16-rerank-cost-optimization-subtasks.md)
- TC-19： [tc-19-search-highlight-subtasks.md](/Users/ryan/personal/smart-vision/docs/tc-19-search-highlight-subtasks.md)

## TC-19 补充说明（2026-04-08）

- 当前状态：Text Search 页面已实现前端本地关键词高亮（基于输入词对返回字段做字符串匹配并标记）。
- 差异点：现状属于“视觉高亮”，TC-19 目标是“检索证据高亮”（由 ES 返回命中片段）。
- 可行性评估：改动中等、影响面可控，主要集中在 ES 查询构建、结果转换、UI 渲染和测试，不涉及分页与 rerank 主链路改造。
- 推荐拆分：详见 [tc-19-search-highlight-subtasks.md](/Users/ryan/personal/smart-vision/docs/tc-19-search-highlight-subtasks.md)。
- 执行状态：`ST-01 ~ ST-06` 已按序完成。
