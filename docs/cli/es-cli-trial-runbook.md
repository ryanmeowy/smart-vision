# ES CLI 试运行与发布手册

更新时间：2026-04-10

## 1. 目标

- 在不影响现有检索链路的前提下，上线并验证 `sv-es` 查询能力。
- 确保鉴权、索引白名单、审计日志、指标监控均可用。

## 2. 发布前检查清单

1. 后端已部署包含 `/api/es/**` 接口的版本。
2. 配置确认：
- `app.search.escli.allowed-index-patterns`（可为空，默认回退向量索引别名/物理索引）
3. 鉴权确认：`@RequireAuth` 已生效，未带 token 返回 `401`。
4. 监控确认：可采集
- `smartvision.escli.requests`
- `smartvision.escli.latency`
5. 审计确认：日志中可检索 `escli_audit` 关键字。

## 3. 试运行步骤（建议 3 天）

### Day 1：小范围开发同学试用

1. CLI 登录

```bash
scripts/sv-es --profile local --base-url http://localhost:8080 login
```

2. 基础命令冒烟

```bash
scripts/sv-es --profile local cluster health
scripts/sv-es --profile local index list --pattern 'smart_*' --size 5
scripts/sv-es --profile local doc search smart_gallery_local_read --q 'fileName:cat' --size 3
```

3. 记录问题：命令可用性、字段可读性、错误提示可理解性。

### Day 2：扩大到 QA / 值班同学

1. 验证高频排查场景（集群状态、索引状态、文档抽样）。
2. 统计 401/403/400 的分布，确认提示是否清晰。
3. 检查指标趋势（QPS、P95）是否平稳。

### Day 3：默认推荐使用

1. 在团队文档中将 `sv-es` 作为优先入口。
2. 保留原 `curl` 方式作为兜底，不做强制切断。

## 4. 回滚策略

1. 快速回滚（推荐）
- 暂停推广 CLI，回到原有 `curl/Kibana` 排查路径。

2. 配置级回滚
- 收紧 `allowed-index-patterns`，仅保留必要索引。

3. 代码级回滚
- 回滚到不包含 `/api/es/**` 的后端版本。

## 5. 常见问题排查

1. `401 Unauthorized`
- 处理：重新执行 `scripts/sv-es --profile <name> login`。

2. `403 Forbidden` / `Index access denied`
- 处理：检查目标索引是否命中 `allowed-index-patterns`。

3. `400 Invalid request parameters`
- 处理：检查 `doc search` 参数（`size`、`sort`、`query` 字符集）。

4. `500 Search backend unavailable`
- 处理：检查 ES 连通性、后端日志、`escli_audit` 失败记录。

## 6. 验收口径（试运行通过）

1. 连续 3 天无 P1/P2 故障。
2. 常见查询场景成功率 >= 95%。
3. 值班同学可独立完成核心排查命令。
4. 审计与指标均可稳定观测。
