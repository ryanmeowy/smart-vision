# A/B Spec: Cloud Embedding Similarity Check

## 目标

对两家云端 embedding 模型做真实调用，并验证向量可用性与相关性表现：

- `doubao-embedding-vision-251215`（Doubao）
- `qwen3-vl-embedding`（Qwen）

固定要求：
- 稠密向量
- 维度 `1024`
- 两类测试：`text -> N text`、`text -> N image_url`
- 相似度：点积（dot product）

## 运行前准备

设置 API Key（默认从环境变量读取）：

```bash
export DASHSCOPE_API_KEY=your_qwen_key
export ARK_API_KEY=your_doubao_key
```

注意：
- Doubao 图片最小尺寸要求为 `14x14`，过小图片会返回 `400 InvalidParameter`。
- 当前 `test_cases.sample.json` 包含 `9` 条 case（`1` 条 text->text + `8` 条 text->image）。
- text->image 样例使用 `16x16` 的 data URI，减少外链 URL 失效带来的波动。

## 运行命令

```bash
python3 docs/lite_knowledge_search_engine_prd/model_ab_spec/ab_vector_similarity.py \
  --input docs/lite_knowledge_search_engine_prd/model_ab_spec/test_cases.sample.json \
  --output /tmp/ab_vector_report.json \
  --dimension 1024 \
  --verbose
```

运行更难样本（每个 case 10 candidates）：

```bash
python3 docs/lite_knowledge_search_engine_prd/model_ab_spec/ab_vector_similarity.py \
  --input docs/lite_knowledge_search_engine_prd/model_ab_spec/test_cases.hard.json \
  --output /tmp/ab_vector_report_hard.json \
  --dimension 1024 \
  --verbose
```

运行业务风格 hard 样本（图表/表格/流程图/架构图）：

```bash
python3 docs/lite_knowledge_search_engine_prd/model_ab_spec/ab_vector_similarity.py \
  --input docs/lite_knowledge_search_engine_prd/model_ab_spec/test_cases.business_hard.json \
  --output /tmp/ab_vector_report_business_hard.json \
  --dimension 1024 \
  --verbose
```

运行扩展业务 hard 样本（27 cases，每 case 19 candidates，含会话式 query + 高混淆图形）：

```bash
python3 docs/lite_knowledge_search_engine_prd/model_ab_spec/ab_vector_similarity.py \
  --input docs/lite_knowledge_search_engine_prd/model_ab_spec/test_cases.business_hard_expanded.json \
  --output /tmp/ab_vector_report_business_hard_expanded.json \
  --dimension 1024 \
  --verbose
```

如需重建业务风格样本：

```bash
python3 docs/lite_knowledge_search_engine_prd/model_ab_spec/build_business_hard_cases.py
```

生成 `>=100` 条混合业务 query（含会话式/口语化噪声表达）：

```bash
python3 docs/lite_knowledge_search_engine_prd/model_ab_spec/build_business_hard_cases.py \
  --target-min-cases 100 \
  --output docs/lite_knowledge_search_engine_prd/model_ab_spec/test_cases.business_hard_100plus.json
```

运行 100+ 测试集：

```bash
python3 docs/lite_knowledge_search_engine_prd/model_ab_spec/ab_vector_similarity.py \
  --input docs/lite_knowledge_search_engine_prd/model_ab_spec/test_cases.business_hard_100plus.json \
  --output /tmp/ab_vector_report_business_hard_100plus.json \
  --dimension 1024 \
  --verbose
```

仅跑 Qwen：

```bash
python3 docs/lite_knowledge_search_engine_prd/model_ab_spec/ab_vector_similarity.py \
  --input docs/lite_knowledge_search_engine_prd/model_ab_spec/test_cases.sample.json \
  --output /tmp/ab_vector_report_qwen.json \
  --dimension 1024 \
  --disable-doubao
```

仅跑 Doubao：

```bash
python3 docs/lite_knowledge_search_engine_prd/model_ab_spec/ab_vector_similarity.py \
  --input docs/lite_knowledge_search_engine_prd/model_ab_spec/test_cases.sample.json \
  --output /tmp/ab_vector_report_doubao.json \
  --dimension 1024 \
  --disable-qwen
```

## 输出说明

输出 JSON 里包括：

- 每个 case 的排序结果与分数
- 每个 case 的 `top1_hit`、`mrr`、正负样本分数差（`positive_negative_gap`）
- 向量健康度（维度、finite、zero norm 检查）
- 每个 provider 的总体统计：`top1_accuracy`、`mrr`、`vector_valid_rate`、平均耗时

## 为什么这些指标能验证“向量正确性”

- `vector_valid_rate`：先确保向量本身是 1024 维、无 NaN/Inf、非零向量。
- `top1_accuracy` / `mrr`：验证相关候选能否排在前面。
- `positive_negative_gap`：验证相关样本分数是否显著高于不相关样本。
