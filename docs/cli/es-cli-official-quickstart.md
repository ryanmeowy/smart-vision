# ES 正式 CLI 快速开始（Picocli）

更新时间：2026-04-10

实现链路说明：`docs/es-cli-request-flow.md`

## 1. 构建

```bash
# 项目根目录
make escli-build

# 或进入子工程直接构建
cd cli/escli
/opt/homebrew/bin/mvn -q test package
```

产物：`cli/escli/target/sv-es-cli.jar`

## 2. 运行

```bash
cd cli/escli
java -jar target/sv-es-cli.jar --help
# 或使用封装脚本
bin/sv-es --help
```

## 3. 登录与会话

```bash
bin/sv-es --profile local --base-url http://localhost:8080 login
bin/sv-es --profile local whoami
```

登录后 token 会写入：`~/.smartvision/escli/<profile>.conf`

## 4. 常用查询

```bash
bin/sv-es --profile local cluster health
bin/sv-es --profile local cluster stats
bin/sv-es --profile local index list --pattern 'smart_*' --format table
bin/sv-es --profile local index stats smart_gallery_local_read
bin/sv-es --profile local mapping get smart_gallery_local_read
bin/sv-es --profile local doc get smart_gallery_local_read 12345 --source true
bin/sv-es --profile local doc search smart_gallery_local_read --q 'fileName:cat' --size 5 --sort createTime:desc
```

## 5. 命令总览（全量）

全局参数（可与子命令混用）：

```bash
--profile <name>
--base-url <url>
--token <token>
--format json|table
--debug
```

会话命令：

```bash
sv-es login
sv-es logout
sv-es whoami
```

集群命令：

```bash
sv-es cluster health
sv-es cluster stats
```

索引命令：

```bash
sv-es index list [--pattern <wildcard>] [--status <green|yellow|red>] [--page <n>] [--size <n>]
sv-es index stats <index>
```

映射命令：

```bash
sv-es mapping get <index>
```

文档命令：

```bash
sv-es doc get <index> <id> [--source true|false]
sv-es doc search <index> --q <query> [--from <n>] [--size <n>] [--sort <field:asc|desc>]... [--include <field>]...
```

## 6. 错误与退出码

1. 非 200 响应将返回非 0 退出码。
2. CLI 会输出：错误码、错误消息、`requestId/errorId`（若有）与排障 hint。

## 7. 与脚本版并存策略

1. 脚本版：`scripts/sv-es`（过渡期保留）
2. 正式版：`cli/escli/bin/sv-es`（推荐）
