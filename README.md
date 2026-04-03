# SmartVision

> 多模态图片搜索引擎 - 基于向量检索与 OCR 融合的智能图片搜索解决方案

[![Java 21](https://img.shields.io/badge/JDK-21-orange.svg)](https://openjdk.org/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Elasticsearch 8](https://img.shields.io/badge/Elasticsearch-8.11+-blue.svg)](https://www.elastic.co/)
[![gRPC](https://img.shields.io/badge/gRPC-Protobuf-red.svg)](https://grpc.io/)
[![License](https://img.shields.io/badge/License-MIT-green)]()

---

## 核心功能

- **多模态搜索**：支持文本搜图、以图搜图、相似图搜索
- **混合检索**：融合向量相似度 (HNSW) 与 OCR 文本匹配 (BM25)
- **云边协同**：Cloud Mode (阿里云 DashScope) / Local Mode (本地 Python 服务)
- **智能标签**：AI 自动生成图片标签与知识图谱
- **高性能**：Redis 向量缓存，毫秒级响应

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend                                │
│                    (Gallery / Upload Widget)                    │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Spring Boot Backend                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Image API   │  │ Vision API  │  │  Async Task Workers     │  │
│  │ /api/v1/    │  │ /api/v1/    │  │  (CompletableFuture)    │  │
│  │  image/     │  │  vision/    │  │                         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              AI Adapter Layer                           │    │
│  │  ┌──────────────┐         ┌──────────────┐              │    │
│  │  │ Cloud Mode   │         │ Local Mode   │              │    │
│  │  │ (DashScope)  │         │ (gRPC/CLIP)  │              │    │
│  │  └──────────────┘         └──────────────┘              │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ┌────────────┐  ┌────────────┐  ┌────────────┐
    │ Redis      │  │ ES 8.x     │  │ OSS        │
    │ (Cache)    │  │ (Vector)   │  │ (Storage)  │
    └────────────┘  └────────────┘  └────────────┘
```

---

## 技术栈

| 分类 | 技术 | 说明 |
|------|------|------|
| **后端** | Java 21, Spring Boot 3.3 | 核心业务逻辑 |
| **RPC** | gRPC, Protobuf | Java-Python 跨语言通信 |
| **AI 服务** | Python 3 | 本地推理 (CLIP, PaddleOCR, LMM) |
| **搜索引擎** | Elasticsearch 8.11 | HNSW 向量索引 + BM25 |
| **缓存** | Redis | 语义缓存，向量缓存 |
| **存储** | Aliyun OSS | 图片存储 |
| **AI API** | Aliyun DashScope | 云端多模态模型 |

---

## 快速开始

### 环境要求

- JDK 21+
- Docker & Docker Compose
- 阿里云账号 (OSS + DashScope)

### 1. 启动基础设施

```bash
# 复制环境变量配置
cp .env.example .env
# 编辑 .env 填写配置

# 启动 Elasticsearch 和 Redis
docker-compose up -d
```

### 2. 配置环境变量

编辑 `.env` 文件：

```env
# Spring Profile
SPRING_PROFILES_ACTIVE=cloud

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# Elasticsearch
ES_HOST=localhost
ES_PASSWORD=your-es-password

# Aliyun OSS
OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
OSS_PUBLIC_ENDPOINT=your-bucket.oss-cn-hangzhou.aliyuncs.com
ALIYUN_ACCESS_KEY_ID=your-access-key
ALIYUN_ACCESS_KEY_SECRET=your-secret-key
ALIYUN_OSS_BUCKET_NAME=your-bucket
ALIYUN_OSS_ROLE_ARN=acs:ram::xxx:role/xxx

# Aliyun DashScope (AI)
DASHSCOPE_API_KEY=your-api-key

# OCR
OCR_ENDPOINT=dashscope.aliyuncs.com
```

### 3. 启动应用

**Cloud Mode** (使用阿里云 API):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=cloud
```

**Local Mode** (使用本地 Python 服务):

```bash
# 1. 启动 Python gRPC 服务 (参考 smart-vision-python 项目)
python server.py

# 2. 启动 Java 后端
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 4. 启动 Streamlit UI（可选）

```bash
cd ui_streamlit
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
streamlit run app.py
```

默认地址：

- `http://localhost:8501`

页面能力：

- `Text Search` / `Search By Image` / `Similar Search` / `Hot Words`
- `Vector Compare`（支持 text-text / image-image / image-text 向量相似度比较）
- `Batch Process`（一体化链路：`/api/v1/auth/sts -> OSS 直传 -> /api/v1/image/batch-tasks`，支持异步任务状态查询与失败重试）

---

## 检索可解释性（TC-04）

`/api/v1/vision/search`、`/api/v1/vision/search-by-image` 返回的每条 `SearchResultDTO` 中新增 `explain` 字段，用于展示命中来源与策略信息：

```json
{
  "id": "123",
  "filename": "cat-photo.jpg",
  "vectorHitStatus": "VECTOR_AND_TEXT",
  "explain": {
    "strategyEffective": "0",
    "hitSources": ["VECTOR", "OCR", "TAG", "GRAPH"],
    "matchedBy": {
      "vector": true,
      "filename": true,
      "ocr": true,
      "tag": true,
      "graph": true
    }
  }
}
```

字段说明：

- `vectorHitStatus`：粗粒度命中状态（`VECTOR_ONLY_LIKE` / `VECTOR_AND_TEXT` / `TEXT_ONLY`）。
- `explain.strategyEffective`：本次结果对应的生效策略编码（`0/1/2/3`）。
- `explain.hitSources`：用于 UI 徽章展示的命中来源集合（`VECTOR/OCR/TAG/GRAPH`）。
- `explain.matchedBy`：字段级命中布尔位（`vector/filename/ocr/tag/graph`）。

说明：`X-Strategy-*` 响应头用于请求级策略调试；`explain` 用于结果级命中解释。

---

## 配置说明

### application.yaml

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE}  # cloud 或 local
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
      database: 0
      timeout: 3000ms
    elasticsearch:
      repositories:
        enabled: true

aliyun:
  oss:
    endpoint: ${OSS_ENDPOINT}
    public-endpoint: ${OSS_PUBLIC_ENDPOINT}
    access-key-id: ${ALIYUN_ACCESS_KEY_ID}
    access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET}
    bucket-name: ${ALIYUN_OSS_BUCKET_NAME}
    role-arn: ${ALIYUN_OSS_ROLE_ARN}
  ocr:
    access-key-id: ${ALIYUN_ACCESS_KEY_ID}
    access-key-secret: ${ALIYUN_ACCESS_KEY_SECRET}
    endpoint: ${OCR_ENDPOINT}

app:
  vector:
    index-name: smart_gallery_${PROFILE}  # cloud 或 local
    dimension: 1024                          # 向量维度
    model-type: ${MODEL_TYPE}               # CLOUD 或 LOCAL
```

### Cloud Mode vs Local Mode

| 配置项 | Cloud Mode | Local Mode |
|--------|------------|------------|
| Profile | `cloud` | `local` |
| 向量模型 | DashScope multimodal-embedding | 本地 CLIP |
| OCR | 阿里云 OCR API | 本地 PaddleOCR |
| 网络 | 需要公网 | 内网即可 |

---

## 项目结构

```
src/main/java/com/smart/vision/core
├── ai                          # AI 服务接口与实现
│   ├── MultiModalEmbeddingService    # 多模态向量服务
│   ├── ImageOcrService               # OCR 服务
│   └── ContentGenerationService      # AIGC 内容生成
├── controller                   # REST API 控制器
│   ├── ImageApiController           # 图片上传接口
│   └── SearchApiController          # 搜索接口
├── service                       # 业务逻辑层
│   ├── ingestion/                  # 图片入库流水线
│   └── search/                    # 搜索服务
├── strategy                       # 检索策略
│   ├── RetrievalStrategy           # 策略接口
│   └── impl/
│       └── HybridRetrievalStrategy  # 混合检索策略
├── repository                    # 持久层
│   └── ImageRepository            # ES 数据访问
├── model                          # 数据模型
│   ├── dto/                       # 请求/响应 DTO
│   ├── entity/                    # ES 文档实体
│   └── enums/                     # 枚举类
├── query                          # ES 查询构建
├── component                      # 通用组件
├── manager                        # 外部服务封装 (ACL)
├── config                         # 配置类
└── constant                       # 常量定义
```

---

## 部署指南

### Docker 部署

```yaml
# docker-compose.yml 示例
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=cloud
    depends_on:
      - elasticsearch
      - redis
    env_file:
      - .env

  elasticsearch:
    image: elasticsearch:8.18.8
    environment:
      - discovery.type=single-node
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
      - xpack.security.enabled=false

  redis:
    image: redis:latest
    command: redis-server --requirepass ${REDIS_PASSWORD}
```

### 云端模式部署

适用于无本地 GPU 的场景，直接调用阿里云 API：

```bash
mvn clean package -DskipTests
java -jar target/smart-vision-0.0.1-SNAPSHOT.jar --spring.profiles.active=cloud
```

### 本地模式部署

适用于高安全要求的内网环境：

```bash
# 1. 部署 Python 服务 (参考 smart-vision-python)
# 2. 通过 FRP 暴露本地服务 (可选)
# 3. 启动 Java 后端
java -jar target/smart-vision-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

---

## Roadmap

- [ ] 视频模态支持 (关键帧提取与视频片段检索)
- [ ] 用户认证与多租户隔离
- [ ] 搜索结果实时高亮
- [ ] 个性化排序

---

## License

MIT License
