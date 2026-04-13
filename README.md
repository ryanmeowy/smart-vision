# SmartVision

> 多模态图片搜索引擎 - 基于向量检索与 OCR 融合的智能图片搜索解决方案

[![Java 21](https://img.shields.io/badge/JDK-21-orange.svg)](https://openjdk.org/)
[![Spring Boot 3.5.8](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Elasticsearch 8](https://img.shields.io/badge/Elasticsearch-8.11+-blue.svg)](https://www.elastic.co/)
[![gRPC](https://img.shields.io/badge/gRPC-Protobuf-red.svg)](https://grpc.io/)
[![License](https://img.shields.io/badge/License-MIT-green)]()

---

## 核心功能

- **多模态搜索**：支持文本搜图、以图搜图、相似图搜索
- **混合检索**：融合向量相似度 (HNSW) 与 OCR 文本匹配 (BM25)
- **重排增强**：多路召回 + RRF 融合 + Cross-Encoder 精排
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

### 5. 指标观测（Actuator + MeterRegistry）

项目中的 `MeterRegistry` 指标通过 Spring Boot Actuator 的 HTTP 接口查看。

1. 在 `src/main/resources/application-local.yaml`（或你使用的 profile 配置）中增加：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

2. 启动服务后访问：

- 指标列表：`GET http://localhost:8080/actuator/metrics`
- 单指标详情：`GET http://localhost:8080/actuator/metrics/{metric.name}`

3. 常用搜索链路指标：

- `smartvision.strategy.selection`
- `smartvision.strategy.fallback`
- `smartvision.search.rerank.calls`
- `smartvision.search.rerank.latency`
- `smartvision.search.rerank.window.size`
- `smartvision.search.rerank.window.ratio`
- `smartvision.search.rerank.window.hit`
- `smartvision.search.rerank.fallback`

4. 命令行示例：

```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/smartvision.search.rerank.calls
curl http://localhost:8080/actuator/metrics/smartvision.search.rerank.latency
```

说明：

- 如果你只看到 `health` 而没有 `metrics`，通常是 `management.endpoints.web.exposure.include` 未包含 `metrics`。
- 目前仓库未默认接入 Prometheus registry；如需 `/actuator/prometheus`，需额外引入 `micrometer-registry-prometheus` 并暴露该端点。
- 详细速查可参考：`docs/observability-metrics-quick-reference.md`

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
├── auth                           # 认证子域（STS、令牌接口）
│   ├── application
│   ├── infrastructure/aliyun
│   └── interfaces/rest
├── common                         # 跨域共享能力
│   ├── api                        # 统一返回模型
│   ├── config                     # 全局配置与 Bean 装配
│   ├── constant                   # 通用常量
│   ├── exception                  # 统一异常与错误码
│   ├── security                   # 鉴权注解/拦截器/加密
│   └── util
├── ingestion                      # 入库流水线子域
│   ├── application(/impl)
│   ├── domain/model
│   ├── infrastructure/(id,persistence/es)
│   └── interfaces/rest(/dto)
├── integration                    # 外部系统适配层
│   ├── ai
│   │   ├── adapter/(cloud,local)
│   │   ├── client
│   │   ├── domain/model
│   │   └── port
│   └── oss
│       ├── domain/model
│       └── task
└── search                         # 搜索子域
    ├── application(/impl,/support)
    ├── domain/(model,port,strategy,util)
    ├── infrastructure
    │   ├── acl                    # search -> integration 防腐层
    │   └── persistence/es
    │   ├── bootstrap
    │   ├── document
    │   ├── query/(factory,spec)
    │   └── repository
    └── interfaces/(rest/dto,assembler)
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
