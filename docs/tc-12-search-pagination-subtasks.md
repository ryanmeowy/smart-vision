# TC-12 子任务清单：搜索分页闭环（分页会话 + 不透明游标）

更新时间：2026-04-08

## 1. 方案确认

- 保持现有接口 `POST /api/v1/vision/search` 不变（兼容旧调用）
- 新增分页接口 `POST /api/v1/vision/search-page`
- 采用“分页会话”机制：首请求完成一次完整排序（含 RRF / rerank），后续翻页基于会话切片，不重复整链路重算
- 前端 Text Search 页面新增“加载更多”，每次加载下一页 10 条

## 2. 子任务拆分（按执行顺序）

| 序号 | 子任务 | 优先级 | 状态 | 主要产出 | 依赖 | 预估 |
|---|---|---|---|---|---|---|
| ST-01 | 定义分页协议 DTO | P0 | 已完成 | `SearchPageQueryDTO`、`SearchPageDTO`、`nextCursor/hasMore` 协议 | 无 | 0.5 天 |
| ST-02 | 设计并实现 Cursor 编解码 | P0 | 已完成 | `SearchCursorPayload`（sessionId/offset/queryFingerprint/expireAt）+ 签名校验 | ST-01 | 0.5 天 |
| ST-03 | 实现分页会话管理器 | P0 | 已完成 | `SearchSessionManager`（Redis 存取/TTL/清理） | ST-02 | 0.5 天 |
| ST-04 | 搜索服务新增分页入口 | P0 | 已完成 | `SmartSearchService#searchPage(...)`；首查建会话，翻页按 offset 切片 | ST-01~03 | 1 天 |
| ST-05 | 新增分页 REST 接口 | P0 | 已完成 | `POST /api/v1/vision/search-page`；错误码与异常映射 | ST-04 | 0.5 天 |
| ST-06 | 结果回填与顺序稳定 | P1 | 已完成 | 按会话中的有序结果切片返回，严格保持排序一致 | ST-04 | 0.5 天 |
| ST-07 | Streamlit Text Search 接入加载更多 | P0 | 已完成 | 首次查第一页，点击按钮带 cursor 取下一页，append 结果 | ST-05 | 0.5-1 天 |
| ST-08 | OpenAPI 文档补充 | P1 | 已完成 | `docs/openapi-ui-draft.yaml` 增加 `/search-page` 协议 | ST-05 | 0.5 天 |
| ST-09 | 单元测试与集成测试 | P0 | 已完成 | 覆盖首查、翻页、末页、过期 cursor、篡改 cursor、指纹不一致 | ST-04~07 | 1 天 |
| ST-10 | 回归与压测验证 | P1 | 待执行 | 稳定性回归、分页一致性验证、P95 对比 | ST-09 | 0.5 天 |

## 3. 代码落点建议

- Controller  
  - `src/main/java/com/smart/vision/core/search/interfaces/rest/SearchApiController.java`
- Service  
  - `src/main/java/com/smart/vision/core/search/application/SmartSearchService.java`
  - `src/main/java/com/smart/vision/core/search/application/impl/SmartSearchServiceImpl.java`
- 新增分页会话组件（建议路径）  
  - `src/main/java/com/smart/vision/core/search/application/support/SearchSessionManager.java`
  - `src/main/java/com/smart/vision/core/search/application/support/SearchCursorCodec.java`
- DTO（建议路径）  
  - `src/main/java/com/smart/vision/core/search/interfaces/rest/dto/SearchPageQueryDTO.java`
  - `src/main/java/com/smart/vision/core/search/interfaces/rest/dto/SearchPageDTO.java`
- 前端  
  - `ui_streamlit/pages.py`

## 4. DoD（验收标准）

1. 同一查询连续翻 3~5 页：无重复、无漏项、顺序稳定  
2. `nextCursor` 在末页为 `null` 且 `hasMore=false`  
3. 篡改/过期 cursor 返回明确错误码与提示信息  
4. 兼容旧接口 `/api/v1/vision/search`（不破坏现有调用）  
5. 新增测试全部通过，核心回归无退化

## 5. 风险与控制

- 风险：会话过期导致翻页失败  
  - 控制：TTL 合理设置（建议 10~15 分钟）+ 前端提示“结果已过期，请重新搜索”
- 风险：数据变化导致跨页不一致  
  - 控制：翻页以会话快照为准；会话内排序固定
- 风险：Redis 存储膨胀  
  - 控制：仅存必要字段（有序 docId + 指纹 + offset 元数据）并设短 TTL

## 6. 关于 Rerank 的影响（确认项）

结论：**有影响，但可控且总体是正向影响。**

- 首次请求：仍会执行一次完整 rerank（与现有搜索主链路一致）
- 后续翻页：**不再重复 rerank**，直接使用首查会话内的有序结果切片
- 直接收益：
  - 避免“每次加载更多都重排”带来的时延和成本上升
  - 避免跨页因重复重算引起的结果抖动
- 注意点：
  - 会话生命周期内排序固定，无法实时反映新入库文档（这是换取稳定分页的一致性代价）
