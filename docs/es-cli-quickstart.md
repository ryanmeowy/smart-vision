# ES CLI 快速使用（sv-es）

更新时间：2026-04-10

## 1. 脚本位置

- 脚本：`scripts/sv-es`

## 2. 基本用法

```bash
scripts/sv-es [--base-url URL] [--token TOKEN] [--profile NAME] [--format json|table] <command>
```

## 3. 登录（推荐）

首次登录并保存 token：

```bash
scripts/sv-es --profile local --base-url http://localhost:8080 login
```

会提示输入 token，保存到本地配置文件（`~/.smartvision/escli/local.conf`）。

查看当前登录状态：

```bash
scripts/sv-es --profile local whoami
```

退出登录：

```bash
scripts/sv-es --profile local logout
```

## 4. profile 配置（可选）

可在本机创建 profile 文件：

- 路径：`~/.smartvision/escli/<profile>.env`
- 示例：`~/.smartvision/escli/local.env`

```bash
SV_ES_BASE_URL=http://localhost:8080
SV_ES_TOKEN=your-access-token
```

使用：

```bash
scripts/sv-es --profile local cluster health
```

## 5. 常用命令示例

1. 查看集群健康

```bash
scripts/sv-es --profile local cluster health
```

2. 查看索引列表（表格）

```bash
scripts/sv-es --profile local --format table index list --pattern 'smart_*' --page 1 --size 20
```

3. 查看索引统计

```bash
scripts/sv-es --profile local index stats smart_gallery_local_read
```

4. 获取单文档

```bash
scripts/sv-es --profile local doc get smart_gallery_local_read 12345 --source true
```

5. 搜索文档

```bash
scripts/sv-es --profile local doc search smart_gallery_local_read \
  --q 'fileName:cat' \
  --from 0 --size 10 \
  --sort createTime:desc \
  --include fileName --include tags
```

## 6. 输出格式

- `--format json`：默认，完整 JSON 输出。
- `--format table`：表格输出（依赖 `jq`；无 `jq` 时自动回退 JSON）。

## 7. 注意事项

1. 所有 `/api/es/**` 接口均需要 `X-Access-Token`（CLI 在 `login` 后会自动携带）。
2. 后端有索引访问白名单控制；未授权索引会返回 `403`。
3. `doc search` 仅支持后端白名单字段和受控 query/sort 语法。
