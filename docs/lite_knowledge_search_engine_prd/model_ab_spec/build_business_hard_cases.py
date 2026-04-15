#!/usr/bin/env python3
"""
Build a business-style hard test set for embedding A/B.

This script generates synthetic business visuals (chart/table/flow/architecture-like)
as PNG data URIs, then writes a ready-to-run JSON test set:
  - text -> N text (hard negatives)
  - text -> N image_url (10 candidates each)
"""

from __future__ import annotations

import argparse
import copy
import base64
import json
import logging
import math
import struct
import zlib
from pathlib import Path
from typing import Dict, List, Tuple


LOGGER = logging.getLogger("build_business_hard_cases")


Color = Tuple[int, int, int]

WHITE: Color = (255, 255, 255)
BLACK: Color = (0, 0, 0)
GRAY: Color = (140, 140, 140)
LIGHT_GRAY: Color = (220, 220, 220)
BLUE: Color = (52, 120, 246)
GREEN: Color = (42, 165, 90)
RED: Color = (220, 70, 70)
ORANGE: Color = (245, 145, 50)
PURPLE: Color = (130, 85, 220)
TEAL: Color = (40, 170, 170)
YELLOW: Color = (245, 210, 60)


def setup_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def new_canvas(width: int = 96, height: int = 96, bg: Color = WHITE) -> List[List[Color]]:
    return [[bg for _ in range(width)] for _ in range(height)]


def set_px(canvas: List[List[Color]], x: int, y: int, color: Color) -> None:
    h = len(canvas)
    w = len(canvas[0])
    if 0 <= x < w and 0 <= y < h:
        canvas[y][x] = color


def fill_rect(canvas: List[List[Color]], x: int, y: int, w: int, h: int, color: Color) -> None:
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            set_px(canvas, xx, yy, color)


def draw_line(canvas: List[List[Color]], x0: int, y0: int, x1: int, y1: int, color: Color) -> None:
    # Bresenham line drawing
    dx = abs(x1 - x0)
    sx = 1 if x0 < x1 else -1
    dy = -abs(y1 - y0)
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        set_px(canvas, x0, y0, color)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def draw_circle(canvas: List[List[Color]], cx: int, cy: int, r: int, color: Color) -> None:
    # Midpoint circle approximation for outline
    x, y = r, 0
    d = 1 - r
    while x >= y:
        for px, py in (
            (cx + x, cy + y),
            (cx + y, cy + x),
            (cx - y, cy + x),
            (cx - x, cy + y),
            (cx - x, cy - y),
            (cx - y, cy - x),
            (cx + y, cy - x),
            (cx + x, cy - y),
        ):
            set_px(canvas, px, py, color)
        y += 1
        if d < 0:
            d += 2 * y + 1
        else:
            x -= 1
            d += 2 * (y - x) + 1


def canvas_to_data_uri(canvas: List[List[Color]]) -> str:
    # Encode RGB canvas into PNG data URI (RGBA, no external dependency).
    h = len(canvas)
    w = len(canvas[0])
    rows = []
    for row in canvas:
        line = bytearray([0])  # no filter
        for r, g, b in row:
            line.extend((r, g, b, 255))
        rows.append(bytes(line))
    raw = b"".join(rows)
    comp = zlib.compress(raw)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack("!I", len(data)) + tag + data + struct.pack("!I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack("!IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", comp)
        + chunk(b"IEND", b"")
    )
    return "data:image/png;base64," + base64.b64encode(png).decode("ascii")


def make_bar_chart() -> str:
    c = new_canvas()
    draw_line(c, 14, 80, 86, 80, BLACK)
    draw_line(c, 14, 80, 14, 14, BLACK)
    fill_rect(c, 22, 58, 8, 22, BLUE)
    fill_rect(c, 36, 48, 8, 32, BLUE)
    fill_rect(c, 50, 38, 8, 42, BLUE)
    fill_rect(c, 64, 28, 8, 52, BLUE)
    fill_rect(c, 78, 22, 8, 58, BLUE)
    return canvas_to_data_uri(c)


def make_line_up() -> str:
    c = new_canvas()
    draw_line(c, 14, 80, 86, 80, BLACK)
    draw_line(c, 14, 80, 14, 14, BLACK)
    pts = [(18, 72), (30, 66), (42, 54), (54, 48), (66, 35), (78, 24), (86, 18)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        draw_line(c, x0, y0, x1, y1, GREEN)
    return canvas_to_data_uri(c)


def make_line_down() -> str:
    c = new_canvas()
    draw_line(c, 14, 80, 86, 80, BLACK)
    draw_line(c, 14, 80, 14, 14, BLACK)
    pts = [(18, 22), (30, 29), (42, 40), (54, 48), (66, 58), (78, 67), (86, 74)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        draw_line(c, x0, y0, x1, y1, RED)
    return canvas_to_data_uri(c)


def make_table_grid() -> str:
    c = new_canvas(bg=LIGHT_GRAY)
    fill_rect(c, 10, 10, 76, 76, WHITE)
    for x in (10, 30, 50, 68, 86):
        draw_line(c, x, 10, x, 86, GRAY)
    for y in (10, 24, 38, 52, 66, 78, 86):
        draw_line(c, 10, y, 86, y, GRAY)
    return canvas_to_data_uri(c)


def make_flowchart() -> str:
    c = new_canvas()
    fill_rect(c, 12, 12, 24, 14, ORANGE)
    fill_rect(c, 60, 12, 24, 14, ORANGE)
    fill_rect(c, 36, 40, 24, 14, ORANGE)
    fill_rect(c, 36, 68, 24, 14, ORANGE)
    draw_line(c, 36, 19, 60, 19, BLACK)
    draw_line(c, 48, 26, 48, 40, BLACK)
    draw_line(c, 48, 54, 48, 68, BLACK)
    return canvas_to_data_uri(c)


def make_pie_chart() -> str:
    c = new_canvas()
    # Fill two half sectors roughly
    fill_rect(c, 20, 20, 56, 56, WHITE)
    draw_circle(c, 48, 48, 28, BLACK)
    for y in range(20, 76):
        for x in range(20, 76):
            dx, dy = x - 48, y - 48
            if dx * dx + dy * dy <= 28 * 28:
                set_px(c, x, y, BLUE if x >= 48 else ORANGE)
    draw_line(c, 48, 20, 48, 76, BLACK)
    return canvas_to_data_uri(c)


def make_architecture() -> str:
    c = new_canvas(bg=LIGHT_GRAY)
    fill_rect(c, 12, 14, 24, 18, WHITE)
    fill_rect(c, 36, 14, 24, 18, WHITE)
    fill_rect(c, 60, 14, 24, 18, WHITE)
    fill_rect(c, 24, 54, 48, 20, WHITE)
    draw_line(c, 24, 32, 48, 54, TEAL)
    draw_line(c, 48, 32, 48, 54, TEAL)
    draw_line(c, 72, 32, 48, 54, TEAL)
    return canvas_to_data_uri(c)


def make_scatter() -> str:
    c = new_canvas()
    draw_line(c, 14, 80, 86, 80, BLACK)
    draw_line(c, 14, 80, 14, 14, BLACK)
    for x, y in [(24, 70), (30, 63), (36, 58), (48, 50), (56, 43), (62, 39), (74, 30), (82, 26)]:
        fill_rect(c, x - 1, y - 1, 3, 3, PURPLE)
    return canvas_to_data_uri(c)


def make_bar_down() -> str:
    c = new_canvas()
    draw_line(c, 14, 80, 86, 80, BLACK)
    draw_line(c, 14, 80, 14, 14, BLACK)
    fill_rect(c, 22, 22, 8, 58, ORANGE)
    fill_rect(c, 36, 28, 8, 52, ORANGE)
    fill_rect(c, 50, 38, 8, 42, ORANGE)
    fill_rect(c, 64, 48, 8, 32, ORANGE)
    fill_rect(c, 78, 58, 8, 22, ORANGE)
    return canvas_to_data_uri(c)


def make_line_flat() -> str:
    c = new_canvas()
    draw_line(c, 14, 80, 86, 80, BLACK)
    draw_line(c, 14, 80, 14, 14, BLACK)
    pts = [(18, 50), (30, 49), (42, 50), (54, 50), (66, 49), (78, 50), (86, 49)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        draw_line(c, x0, y0, x1, y1, TEAL)
    return canvas_to_data_uri(c)


def make_area_up() -> str:
    c = new_canvas()
    draw_line(c, 14, 80, 86, 80, BLACK)
    draw_line(c, 14, 80, 14, 14, BLACK)
    pts = [(18, 70), (30, 63), (42, 56), (54, 48), (66, 39), (78, 30), (86, 24)]
    # Fill rough area to x-axis for area chart effect.
    for x, y in pts:
        draw_line(c, x, y, x, 80, LIGHT_GRAY)
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        draw_line(c, x0, y0, x1, y1, GREEN)
    return canvas_to_data_uri(c)


def make_heatmap() -> str:
    c = new_canvas(bg=WHITE)
    fill_rect(c, 14, 14, 68, 68, LIGHT_GRAY)
    cell = 12
    colors = [LIGHT_GRAY, YELLOW, ORANGE, RED]
    for r in range(5):
        for col in range(5):
            x = 16 + col * cell
            y = 16 + r * cell
            idx = (r * 2 + col * 3) % len(colors)
            fill_rect(c, x, y, 10, 10, colors[idx])
    return canvas_to_data_uri(c)


def make_gantt() -> str:
    c = new_canvas(bg=WHITE)
    draw_line(c, 12, 20, 84, 20, GRAY)
    for x in (20, 32, 44, 56, 68, 80):
        draw_line(c, x, 20, x, 82, LIGHT_GRAY)
    fill_rect(c, 24, 28, 20, 8, BLUE)
    fill_rect(c, 36, 42, 28, 8, GREEN)
    fill_rect(c, 52, 56, 24, 8, ORANGE)
    fill_rect(c, 18, 70, 22, 8, PURPLE)
    return canvas_to_data_uri(c)


def make_funnel() -> str:
    c = new_canvas(bg=WHITE)
    fill_rect(c, 20, 18, 56, 10, BLUE)
    fill_rect(c, 26, 34, 44, 10, TEAL)
    fill_rect(c, 32, 50, 32, 10, ORANGE)
    fill_rect(c, 38, 66, 20, 10, RED)
    return canvas_to_data_uri(c)


def make_kpi_cards() -> str:
    c = new_canvas(bg=LIGHT_GRAY)
    fill_rect(c, 10, 12, 36, 28, WHITE)
    fill_rect(c, 50, 12, 36, 28, WHITE)
    fill_rect(c, 10, 48, 36, 28, WHITE)
    fill_rect(c, 50, 48, 36, 28, WHITE)
    fill_rect(c, 14, 18, 20, 4, BLUE)
    fill_rect(c, 54, 18, 20, 4, GREEN)
    fill_rect(c, 14, 54, 20, 4, ORANGE)
    fill_rect(c, 54, 54, 20, 4, RED)
    return canvas_to_data_uri(c)


def make_swimlane() -> str:
    c = new_canvas(bg=WHITE)
    for y in (18, 40, 62, 84):
        draw_line(c, 10, y, 86, y, GRAY)
    fill_rect(c, 18, 24, 16, 8, BLUE)
    fill_rect(c, 42, 24, 18, 8, BLUE)
    fill_rect(c, 24, 46, 18, 8, GREEN)
    fill_rect(c, 50, 46, 20, 8, GREEN)
    fill_rect(c, 30, 68, 20, 8, ORANGE)
    draw_line(c, 34, 28, 42, 28, BLACK)
    draw_line(c, 42, 28, 42, 50, BLACK)
    draw_line(c, 42, 50, 50, 50, BLACK)
    draw_line(c, 60, 50, 60, 72, BLACK)
    return canvas_to_data_uri(c)


def make_network_mesh() -> str:
    c = new_canvas(bg=LIGHT_GRAY)
    nodes = [(20, 20), (48, 18), (76, 24), (26, 48), (52, 48), (76, 54), (38, 76), (64, 76)]
    for x0, y0 in nodes:
        for x1, y1 in nodes:
            if (x0, y0) < (x1, y1) and abs(x0 - x1) + abs(y0 - y1) < 46:
                draw_line(c, x0, y0, x1, y1, TEAL)
    for x, y in nodes:
        fill_rect(c, x - 2, y - 2, 5, 5, BLUE)
    return canvas_to_data_uri(c)


def make_timeline() -> str:
    c = new_canvas()
    draw_line(c, 12, 48, 84, 48, BLACK)
    for x in (16, 30, 44, 58, 72, 84):
        fill_rect(c, x - 2, 46, 5, 5, BLUE)
        draw_line(c, x, 30, x, 46, GRAY)
    fill_rect(c, 14, 18, 12, 10, LIGHT_GRAY)
    fill_rect(c, 42, 18, 12, 10, LIGHT_GRAY)
    fill_rect(c, 70, 18, 12, 10, LIGHT_GRAY)
    return canvas_to_data_uri(c)


def make_doc_page() -> str:
    c = new_canvas(bg=LIGHT_GRAY)
    fill_rect(c, 18, 10, 60, 76, WHITE)
    fill_rect(c, 22, 16, 52, 6, BLUE)
    y = 28
    while y < 78:
        fill_rect(c, 22, y, 50, 3, GRAY)
        y += 8
    return canvas_to_data_uri(c)


def build_image_catalog() -> Dict[str, str]:
    LOGGER.info("Building business-style image catalog ...")
    catalog = {
        "img_bar_chart": make_bar_chart(),
        "img_bar_down": make_bar_down(),
        "img_line_up": make_line_up(),
        "img_line_flat": make_line_flat(),
        "img_line_down": make_line_down(),
        "img_area_up": make_area_up(),
        "img_table_grid": make_table_grid(),
        "img_heatmap": make_heatmap(),
        "img_flowchart": make_flowchart(),
        "img_swimlane": make_swimlane(),
        "img_pie_chart": make_pie_chart(),
        "img_funnel": make_funnel(),
        "img_architecture": make_architecture(),
        "img_network_mesh": make_network_mesh(),
        "img_scatter": make_scatter(),
        "img_timeline": make_timeline(),
        "img_gantt": make_gantt(),
        "img_kpi_cards": make_kpi_cards(),
        "img_doc_page": make_doc_page(),
    }
    LOGGER.info("Image catalog size=%d", len(catalog))
    return catalog


def build_text_cases() -> List[Dict]:
    def tc(case_id: str, query: str, positives: List[str], hard_negatives: List[str], noise_negatives: List[str]) -> Dict:
        shared_hard_negatives = [
            "只要模型分数高，就不需要人工标注评测集。",
            "线上 P95 下降一定意味着离线 MRR 提升。",
            "多模态检索里 text->image 和 text->text 的评估口径完全相同。",
            "把相似度函数从 dot 改成 L2 就能自动提升所有业务 query 的准确率。",
            "候选集越小越能体现模型真实能力，因此不需要 hard negative。",
            "embedding 模型升级后不需要做回归评测，只看一次抽样就够。",
            "只要向量维度一致，就说明跨模型向量可直接混用。",
            "检索排序错误通常只与前端展示有关，与向量语义无关。",
        ]
        candidates: List[Dict] = []
        for idx, text in enumerate(positives, 1):
            candidates.append(
                {
                    "id": f"txt_rel_{idx}",
                    "type": "text",
                    "text": text,
                    "expected_relevant": True,
                }
            )
        for idx, text in enumerate(hard_negatives, 1):
            candidates.append(
                {
                    "id": f"txt_hneg_{idx}",
                    "type": "text",
                    "text": text,
                    "expected_relevant": False,
                }
            )
        for idx, text in enumerate(noise_negatives, 1):
            candidates.append(
                {
                    "id": f"txt_neg_{idx}",
                    "type": "text",
                    "text": text,
                    "expected_relevant": False,
                }
            )
        for idx, text in enumerate(shared_hard_negatives, 1):
            candidates.append(
                {
                    "id": f"txt_sneg_{idx}",
                    "type": "text",
                    "text": text,
                    "expected_relevant": False,
                }
            )
        return {"case_id": case_id, "test_type": "text_to_text", "query": query, "candidates": candidates}

    return [
        tc(
            "biz_t2t_001",
            "如何解释 Recall、MRR、NDCG 在检索评估中的差异？",
            [
                "Recall 关注命中覆盖，MRR关注首个相关结果位置，NDCG适合分级相关。",
                "NDCG 能利用 graded relevance，比二值指标更细粒度。",
                "MRR 对第一条结果很敏感，常配合 Recall 一起看。",
            ],
            [
                "Recall@10 和 Recall@100 的差异，本质是截断深度变化，不代表模型本体一定更好。",
                "NDCG 与 MRR 都看排序，但 NDCG 能纳入多条相关文档的位次信息。",
                "单看 Recall 可能掩盖首条结果体验差的问题，因此常与 MRR 联合评估。",
                "如果标注只有二值相关，NDCG 与 MRR 对排序差异的灵敏度不同。",
            ],
            [
                "P95 直接等价于 NDCG，二者可以互换。",
                "分页 cursor 的编码方式与 NDCG 定义相同。",
                "OCR 文本提取失败后如何自动重试。",
                "对象存储图片压缩参数与质量平衡。",
            ],
        ),
        tc(
            "biz_t2t_002",
            "窗口化 rerank 的核心作用是什么？",
            [
                "窗口化 rerank 通过只重排前窗口候选，减少延迟与成本。",
                "常配合 fusion 分，避免只看 rerank 导致排序抖动。",
                "窗口参数通常有 window-size/factor/min/max。",
            ],
            [
                "窗口化的目标是在有限预算里提升头部排序质量，而不是重排全部候选。",
                "rerank 常用于压制 embedding 的语义漂移，尤其是长尾 query。",
                "窗口太小可能丢召回，窗口太大则会增加时延与费用。",
                "可以通过离线网格搜索 window-size 与 factor 找到质量-时延平衡点。",
            ],
            [
                "窗口化用于设置 ES 分片和副本数量。",
                "窗口化是 OCR 图片裁剪策略。",
                "模型温度参数会决定窗口大小。",
                "部署灰度策略中 5%->20% 的升级流程。",
            ],
        ),
        tc(
            "biz_t2t_003",
            "文搜图里，用户说“差不多像业务流程图那种”，该怎么提升召回鲁棒性？",
            [
                "可扩展 query 到流程图同义词（流程编排、步骤图、泳道图）并融合多路召回。",
                "先用 embedding 粗召回，再用跨模态 rerank 区分流程图与架构图。",
                "对会话式表达做 query rewrite，补充结构特征词如节点、连线、分支。",
            ],
            [
                "对模糊描述可以加入视觉结构词，减少只匹配主题词导致的错召回。",
                "流程图和架构图语义接近，建议构造 hard negative 做对比训练或评测。",
                "可通过多查询重写并行召回提高覆盖，再用重排保证头部精度。",
                "用户口语“那种图”通常缺失实体词，需要补足图形拓扑信息。",
            ],
            [
                "直接把 topK 从 20 提到 200 就一定能解决。",
                "关闭向量归一化后流程图会更容易被识别。",
                "把图片全部压成 32x32 再检索会更稳定。",
                "只保留发布时间最新的图片即可提升流程图召回。",
            ],
        ),
        tc(
            "biz_t2t_004",
            "怎么验证 embedding 输出向量本身是健康的？",
            [
                "先检查维度一致性、空向量、NaN/Inf，再看向量范数分布是否异常。",
                "用正负样本对看分数间隔（positive-negative gap）是否稳定大于0。",
                "做重复请求一致性验证，确保同一输入向量变化在可接受范围内。",
            ],
            [
                "健康检查应覆盖批量请求和单请求，排查截断、乱码、超时重试副作用。",
                "可监控向量均值和方差漂移，及时发现模型版本切换问题。",
                "异常高相似度全局上升可能是向量塌缩信号，需要立即告警。",
                "同一 query 在不同时间出现大幅波动，可能与输入预处理不一致有关。",
            ],
            [
                "只要 API 返回 200，向量质量就一定没问题。",
                "向量维度越大，向量一定越正确。",
                "把 cosine 换成 dot 就能自动修复向量异常。",
                "模型名字里带 vision 就不需要做向量健康检查。",
            ],
        ),
        tc(
            "biz_t2t_005",
            "预算有限时，怎么在召回效果和成本之间做取舍？",
            [
                "先固定体验底线（如 Top1/MRR），再对 topK、rerank 窗口和多路召回做成本敏感优化。",
                "通过分层策略把高价值 query 走重排，低价值 query 走轻量链路。",
                "离线先做 Pareto 前沿，线上按预算动态切换策略档位。",
            ],
            [
                "成本优化不应只看单次调用，要看 QPS 下总 token 与时延尾部。",
                "可引入 query 复杂度分桶，不同桶走不同检索配置。",
                "多模型路由可把简单 query 下沉到便宜模型，复杂 query 走高质量模型。",
                "盲目压缩 topK 可能导致长尾 query 质量骤降，需要分桶评估。",
            ],
            [
                "把所有 query 都固定走最大模型就是最省钱方案。",
                "只要减少日志采样率，检索成本会自动下降。",
                "禁用评估指标可以显著降低线上推理费用。",
                "把 embedding 维度改成 1 就能零成本运行。",
            ],
        ),
        tc(
            "biz_t2t_006",
            "hard negative 用例怎么设计才真正有区分度？",
            [
                "优先选择语义接近但标签不同的样本，例如流程图 vs 架构图。",
                "负样本要覆盖词面近似、结构近似和场景近似，避免只做随机负采样。",
                "每条 query 至少放入多条 hard negative，观察模型是否稳定压住误匹配。",
            ],
            [
                "如果负样本太容易，Top1 会虚高，无法反映真实业务难度。",
                "可用错误分析回流线上误召回结果，持续补充 hard negative 池。",
                "hard negative 需要定期更新，避免模型记住固定模板后过拟合。",
                "将高频误判类型拆分评估，能更快定位模型短板。",
            ],
            [
                "负样本越少越能证明模型能力强。",
                "只要 query 写得长，hard negative 就不重要。",
                "随机打乱候选顺序会替代 hard negative 设计。",
                "hard negative 和脏数据是同一个概念。",
            ],
        ),
    ]


def build_image_cases(catalog: Dict[str, str]) -> List[Dict]:
    order = list(catalog.keys())
    case_specs = [
        ("biz_t2i_001", "柱状图，后几列明显更高", {"img_bar_chart"}),
        ("biz_t2i_002", "柱状图，前几列更高，整体下滑", {"img_bar_down"}),
        ("biz_t2i_003", "趋势向上的折线图", {"img_line_up"}),
        ("biz_t2i_004", "走势基本平稳，近乎水平的折线", {"img_line_flat"}),
        ("biz_t2i_005", "趋势下滑的折线图", {"img_line_down"}),
        ("biz_t2i_006", "面积图风格，趋势向上且下方有填充区域", {"img_area_up"}),
        ("biz_t2i_007", "表格截图，网格结构清晰", {"img_table_grid"}),
        ("biz_t2i_008", "热力图，矩阵格子通过颜色深浅表达数值", {"img_heatmap"}),
        ("biz_t2i_009", "流程图，多个方框通过连线连接", {"img_flowchart", "img_swimlane"}),
        ("biz_t2i_010", "泳道图，横向分层的流程步骤", {"img_swimlane", "img_flowchart"}),
        ("biz_t2i_011", "饼图占比展示", {"img_pie_chart"}),
        ("biz_t2i_012", "漏斗图，上宽下窄的分层转化结构", {"img_funnel"}),
        ("biz_t2i_013", "系统架构图，多个模块汇聚到下游", {"img_architecture", "img_network_mesh"}),
        ("biz_t2i_014", "网络拓扑图，多节点网状连接", {"img_network_mesh", "img_architecture"}),
        ("biz_t2i_015", "时间线样式，多个里程碑节点", {"img_timeline"}),
        ("biz_t2i_016", "项目排期图，横向任务条贴着时间轴", {"img_gantt", "img_timeline"}),
        ("biz_t2i_017", "散点图风格，点位分布在坐标系中", {"img_scatter"}),
        ("biz_t2i_018", "文档页面截图，有标题条和多行正文", {"img_doc_page", "img_table_grid"}),
        ("biz_t2i_019", "仪表盘卡片，多块指标面板", {"img_kpi_cards"}),
        ("biz_t2i_020", "我想找那种项目计划排期图，不是普通折线图", {"img_gantt"}),
        ("biz_t2i_021", "有没有那种路线图感觉、带多个节点阶段的图", {"img_timeline", "img_gantt"}),
    ]

    out: List[Dict] = []
    for cid, query, rel_set in case_specs:
        cands = []
        for key in order:
            cands.append(
                {
                    "id": key,
                    "type": "image_url",
                    "image_url": catalog[key],
                    "expected_relevant": key in rel_set,
                }
            )
        out.append(
            {
                "case_id": cid,
                "test_type": "text_to_image",
                "query": query,
                "candidates": cands,
            }
        )
    return out


def build_query_variants(query: str, test_type: str, variants_per_case: int) -> List[str]:
    """
    Build realistic noisy query variants for each base query.

    Variant strategy:
    - keep original query as v01
    - append conversational, business-context, and noisy expressions
    - keep semantics unchanged to avoid label drift
    """
    if variants_per_case <= 1:
        return [query]

    if test_type == "text_to_image":
        templates = [
            "帮我找一下：{q}，偏业务汇报场景。",
            "口语化一点说，我想要“{q}”这种，不要太泛。",
            "线上验收要用，目标是 {q}，请优先相关性。",
            "{q}，大概就是老板周会里常见那类图。",
            "这个描述可能不专业：{q}，你按业务图表理解就行。",
            "急用，查一下 {q}，尽量别召回到结构很像但语义不对的图。",
        ]
    else:
        templates = [
            "请解释下：{q}，给我偏实战一点。",
            "我们在做检索复盘，核心问题是：{q}",
            "口语化问法：{q}，重点说可落地做法。",
            "线上同学反馈想看这个：{q}，尽量简明。",
            "这条 query 有点噪声，但本质在问：{q}",
            "帮我快速澄清：{q}，用于评审会同步。",
        ]

    suffixes = [
        "",
        "（业务同学原话）",
        "（含口语噪声）",
        "（线上反馈）",
        "（评审场景）",
    ]

    variants = [query]
    idx = 0
    while len(variants) < variants_per_case:
        template = templates[idx % len(templates)]
        suffix = suffixes[(idx // len(templates)) % len(suffixes)]
        variants.append(template.format(q=query) + suffix)
        idx += 1
    return variants


def expand_tests_with_query_noise(base_tests: List[Dict], variants_per_case: int) -> List[Dict]:
    """Expand each base test into multiple noisy-query variants."""
    out: List[Dict] = []
    for test in base_tests:
        variants = build_query_variants(test["query"], test["test_type"], variants_per_case)
        for i, qv in enumerate(variants, start=1):
            new_test = copy.deepcopy(test)
            new_test["case_id"] = f"{test['case_id']}_v{i:02d}"
            new_test["query"] = qv
            out.append(new_test)
    return out


def build_dataset(variants_per_case: int = 1, target_min_cases: int = 0) -> Dict:
    catalog = build_image_catalog()
    base_tests = build_text_cases() + build_image_cases(catalog)

    effective_variants = max(1, variants_per_case)
    if target_min_cases > 0:
        # Auto-scale variants to satisfy minimum case requirement.
        effective_variants = max(effective_variants, math.ceil(target_min_cases / len(base_tests)))

    tests = expand_tests_with_query_noise(base_tests, effective_variants)
    LOGGER.info(
        "Expanded dataset with noisy query variants: base_cases=%d variants_per_case=%d final_cases=%d",
        len(base_tests),
        effective_variants,
        len(tests),
    )
    return {"tests": tests}


def main() -> int:
    parser = argparse.ArgumentParser(description="Build business-style hard test cases for vector similarity A/B.")
    parser.add_argument(
        "--output",
        default="/Users/ryan/personal/smart-vision/docs/lite_knowledge_search_engine_prd/model_ab_spec/test_cases.business_hard.json",
        help="output json path",
    )
    parser.add_argument(
        "--variants-per-case",
        type=int,
        default=1,
        help="number of query variants per base case (default: 1)",
    )
    parser.add_argument(
        "--target-min-cases",
        type=int,
        default=0,
        help="minimum total case count; script auto-increases variants if needed (default: 0)",
    )
    args = parser.parse_args()

    setup_logging()
    dataset = build_dataset(variants_per_case=args.variants_per_case, target_min_cases=args.target_min_cases)
    output_path = Path(args.output).expanduser().resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(dataset, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    counts = [len(x.get("candidates", [])) for x in dataset["tests"]]
    LOGGER.info("Wrote dataset: %s", output_path)
    LOGGER.info("cases=%d min_candidates=%d max_candidates=%d", len(dataset["tests"]), min(counts), max(counts))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
