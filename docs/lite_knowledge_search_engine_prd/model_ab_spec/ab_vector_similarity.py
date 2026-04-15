#!/usr/bin/env python3
"""
Cloud embedding A/B verification script.

What this script does:
1. Call Qwen3-VL-Embedding and Doubao-Embedding-Vision cloud APIs.
2. Force dense vectors with dimension=1024.
3. Run two test types:
   - text query -> N text candidates
   - text query -> N image_url candidates
4. Compute dot-product similarities and rank candidates.
5. Output scores, rankings, and validation metrics.

Notes:
- API keys default to environment variables:
  - DASHSCOPE_API_KEY (Qwen)
  - ARK_API_KEY (Doubao)
- If one provider key is missing, the script continues with the other provider.
"""

from __future__ import annotations

import argparse
import json
import logging
import math
import os
import time
import traceback
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple


LOGGER = logging.getLogger("ab_vector_similarity")


def setup_logging(verbose: bool) -> None:
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        obj = json.load(handle)
    if not isinstance(obj, dict):
        raise ValueError(f"{path} must be a JSON object")
    return obj


def save_json(path: Path, obj: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(obj, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def dot_product(vec_a: Sequence[float], vec_b: Sequence[float]) -> float:
    if len(vec_a) != len(vec_b):
        raise ValueError(f"vector length mismatch: {len(vec_a)} != {len(vec_b)}")
    return float(sum(a * b for a, b in zip(vec_a, vec_b)))


def is_valid_vector(vec: Sequence[float], expected_dim: int) -> Tuple[bool, str]:
    if len(vec) != expected_dim:
        return False, f"dimension_mismatch:{len(vec)}"
    finite_ok = all(math.isfinite(v) for v in vec)
    if not finite_ok:
        return False, "contains_non_finite"
    squared_sum = sum(v * v for v in vec)
    if squared_sum <= 0:
        return False, "zero_norm"
    return True, "ok"


class HttpClient:
    """Small HTTP wrapper with retry and structured error messages."""

    def __init__(self, timeout_ms: int, max_retries: int, retry_sleep_ms: int):
        self.timeout_sec = timeout_ms / 1000.0
        self.max_retries = max_retries
        self.retry_sleep_ms = retry_sleep_ms

    def post_json(self, url: str, headers: Dict[str, str], payload: Dict[str, Any]) -> Dict[str, Any]:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(
            url=url,
            data=body,
            method="POST",
            headers=headers,
        )

        last_exc: Optional[Exception] = None
        for attempt in range(self.max_retries + 1):
            started = time.perf_counter()
            try:
                with urllib.request.urlopen(req, timeout=self.timeout_sec) as resp:
                    resp_text = resp.read().decode("utf-8")
                elapsed_ms = (time.perf_counter() - started) * 1000.0
                data = json.loads(resp_text)
                if not isinstance(data, dict):
                    raise ValueError("response is not JSON object")
                data["_latency_ms"] = elapsed_ms
                return data
            except urllib.error.HTTPError as exc:
                err_text = ""
                try:
                    err_text = exc.read().decode("utf-8", errors="replace")
                except Exception:  # pylint: disable=broad-except
                    err_text = "<failed_to_read_http_error_body>"
                last_exc = RuntimeError(f"http_{exc.code}: {err_text[:500]}")
                if attempt >= self.max_retries:
                    break
                sleep_ms = self.retry_sleep_ms * (2 ** attempt)
                LOGGER.warning("HTTP call failed (attempt %s/%s), retrying in %sms: %s", attempt + 1, self.max_retries + 1, sleep_ms, last_exc)
                time.sleep(sleep_ms / 1000.0)
            except (urllib.error.URLError, ValueError, json.JSONDecodeError) as exc:
                last_exc = exc
                if attempt >= self.max_retries:
                    break
                sleep_ms = self.retry_sleep_ms * (2 ** attempt)
                LOGGER.warning("HTTP call failed (attempt %s/%s), retrying in %sms: %s", attempt + 1, self.max_retries + 1, sleep_ms, exc)
                time.sleep(sleep_ms / 1000.0)

        raise RuntimeError(f"HTTP request failed after retries: {last_exc}")


class EmbeddingProvider:
    """Abstract provider interface."""

    def name(self) -> str:
        raise NotImplementedError

    def embed_text(self, text: str) -> Dict[str, Any]:
        raise NotImplementedError

    def embed_image(self, image_url: str) -> Dict[str, Any]:
        raise NotImplementedError


class QwenProvider(EmbeddingProvider):
    """Qwen multimodal embedding provider via DashScope official endpoint."""

    def __init__(self, api_key: str, model: str, dimension: int, http_client: HttpClient):
        self.api_key = api_key
        self.model = model
        self.dimension = dimension
        self.http = http_client
        self.endpoint = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding"

    def name(self) -> str:
        return "qwen3-vl-embedding"

    def _call(self, content_obj: Dict[str, Any]) -> Dict[str, Any]:
        payload = {
            "model": self.model,
            "input": {"contents": [content_obj]},
            # Dense vector + controlled dimension
            "parameters": {
                "dimension": self.dimension,
            },
        }
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        data = self.http.post_json(self.endpoint, headers, payload)
        embeddings = data.get("output", {}).get("embeddings", [])
        if not embeddings:
            raise RuntimeError(f"Qwen response missing embeddings: {str(data)[:400]}")
        vector = embeddings[0].get("embedding")
        if not isinstance(vector, list):
            raise RuntimeError("Qwen embedding field is not list")
        usage = data.get("usage", {}) or {}
        return {
            "vector": [float(x) for x in vector],
            "latency_ms": float(data.get("_latency_ms", 0.0)),
            "usage": {
                "input_tokens": int(usage.get("input_tokens", 0) or 0),
                "image_tokens": int(usage.get("image_tokens", 0) or 0),
                "total_tokens": int(usage.get("total_tokens", 0) or 0),
            },
        }

    def embed_text(self, text: str) -> Dict[str, Any]:
        return self._call({"text": text})

    def embed_image(self, image_url: str) -> Dict[str, Any]:
        return self._call({"image": image_url})


class DoubaoProvider(EmbeddingProvider):
    """Doubao multimodal embedding provider via ARK official endpoint."""

    def __init__(self, api_key: str, model: str, dimension: int, http_client: HttpClient):
        self.api_key = api_key
        self.model = model
        self.dimension = dimension
        self.http = http_client
        self.endpoint = "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal"

    def name(self) -> str:
        return "doubao-embedding-vision"

    def _call(self, input_item: Dict[str, Any]) -> Dict[str, Any]:
        payload = {
            "model": self.model,
            "input": [input_item],
            "dimensions": self.dimension,
        }
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        data = self.http.post_json(self.endpoint, headers, payload)
        vector = data.get("data", {}).get("embedding")
        if not isinstance(vector, list):
            raise RuntimeError(f"Doubao response missing embedding: {str(data)[:400]}")
        usage = data.get("usage", {}) or {}
        details = usage.get("prompt_tokens_details", {}) or {}
        return {
            "vector": [float(x) for x in vector],
            "latency_ms": float(data.get("_latency_ms", 0.0)),
            "usage": {
                "prompt_tokens": int(usage.get("prompt_tokens", 0) or 0),
                "text_tokens": int(details.get("text_tokens", 0) or 0),
                "image_tokens": int(details.get("image_tokens", 0) or 0),
            },
        }

    def embed_text(self, text: str) -> Dict[str, Any]:
        return self._call({"type": "text", "text": text})

    def embed_image(self, image_url: str) -> Dict[str, Any]:
        return self._call({"type": "image_url", "image_url": {"url": image_url}})


def build_providers(args: argparse.Namespace) -> List[EmbeddingProvider]:
    providers: List[EmbeddingProvider] = []
    http_client = HttpClient(
        timeout_ms=args.timeout_ms,
        max_retries=args.max_retries,
        retry_sleep_ms=args.retry_sleep_ms,
    )

    if args.enable_qwen:
        if args.qwen_api_key:
            providers.append(QwenProvider(args.qwen_api_key, args.qwen_model, args.dimension, http_client))
        else:
            LOGGER.warning("Skip qwen: DASHSCOPE_API_KEY missing.")

    if args.enable_doubao:
        if args.doubao_api_key:
            providers.append(DoubaoProvider(args.doubao_api_key, args.doubao_model, args.dimension, http_client))
        else:
            LOGGER.warning("Skip doubao: ARK_API_KEY missing.")

    if not providers:
        raise RuntimeError("No provider available. Set DASHSCOPE_API_KEY and/or ARK_API_KEY.")
    return providers


def rank_candidates(
    provider: EmbeddingProvider,
    query_vec: Sequence[float],
    candidates: List[Dict[str, Any]],
    expected_dim: int,
) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    """
    Embed all candidates, compute dot product with query vector, and return sorted ranking.
    Also returns vector quality stats for this ranking call.
    """
    ranking: List[Dict[str, Any]] = []
    valid_count = 0
    invalid_count = 0
    invalid_reasons: List[str] = []
    embed_latency_ms_sum = 0.0

    for candidate in candidates:
        cid = str(candidate.get("id", "")).strip()
        ctype = str(candidate.get("type", "")).strip()
        if not cid:
            raise ValueError("candidate missing id")
        if ctype not in {"text", "image_url"}:
            raise ValueError(f"candidate id={cid} invalid type={ctype}")

        if ctype == "text":
            text = str(candidate.get("text", "")).strip()
            if not text:
                raise ValueError(f"candidate id={cid} type=text missing text")
            emb = provider.embed_text(text)
        else:
            image_url = str(candidate.get("image_url", "")).strip()
            if not image_url:
                raise ValueError(f"candidate id={cid} type=image_url missing image_url")
            emb = provider.embed_image(image_url)

        cvec = emb["vector"]
        embed_latency_ms_sum += float(emb.get("latency_ms", 0.0))
        ok, reason = is_valid_vector(cvec, expected_dim)
        if ok:
            valid_count += 1
            score = dot_product(query_vec, cvec)
        else:
            invalid_count += 1
            invalid_reasons.append(f"{cid}:{reason}")
            score = float("-inf")

        ranking.append(
            {
                "candidate_id": cid,
                "candidate_type": ctype,
                "score": score,
                "expected_relevant": bool(candidate.get("expected_relevant", False)),
                "vector_valid": ok,
                "vector_invalid_reason": None if ok else reason,
            }
        )

    ranking.sort(key=lambda x: x["score"], reverse=True)
    return ranking, {
        "candidate_vector_valid": valid_count,
        "candidate_vector_invalid": invalid_count,
        "candidate_vector_invalid_details": invalid_reasons,
        "candidate_embed_latency_ms_sum": round(embed_latency_ms_sum, 3),
    }


def evaluate_case_ranking(ranking: List[Dict[str, Any]]) -> Dict[str, Any]:
    """
    Compute retrieval-style quality indicators to verify embedding behavior.
    """
    relevant_positions: List[int] = []
    positive_scores: List[float] = []
    negative_scores: List[float] = []

    for idx, row in enumerate(ranking, start=1):
        score = float(row["score"])
        if row["expected_relevant"]:
            relevant_positions.append(idx)
            if math.isfinite(score):
                positive_scores.append(score)
        else:
            if math.isfinite(score):
                negative_scores.append(score)

    top1_hit = 1.0 if relevant_positions and relevant_positions[0] == 1 else 0.0
    mrr = 1.0 / relevant_positions[0] if relevant_positions else 0.0
    avg_pos = sum(positive_scores) / len(positive_scores) if positive_scores else None
    avg_neg = sum(negative_scores) / len(negative_scores) if negative_scores else None
    gap = (avg_pos - avg_neg) if (avg_pos is not None and avg_neg is not None) else None

    return {
        "top1_hit": top1_hit,
        "mrr": mrr,
        "relevant_positions": relevant_positions,
        "avg_positive_score": avg_pos,
        "avg_negative_score": avg_neg,
        "positive_negative_gap": gap,
    }


def run_provider_eval(
    provider: EmbeddingProvider,
    tests: List[Dict[str, Any]],
    expected_dim: int,
) -> Dict[str, Any]:
    LOGGER.info("Start evaluating provider=%s, cases=%d", provider.name(), len(tests))
    results: List[Dict[str, Any]] = []
    case_errors: List[str] = []
    total_top1 = 0.0
    total_mrr = 0.0
    total_valid_vectors = 0
    total_invalid_vectors = 0
    total_latency_ms = 0.0

    for i, case in enumerate(tests, start=1):
        case_id = str(case.get("case_id", f"case-{i:03d}"))
        test_type = str(case.get("test_type", "")).strip()
        query = str(case.get("query", "")).strip()
        candidates = case.get("candidates", [])

        LOGGER.info("Case %s (%s): start", case_id, test_type)
        try:
            if test_type not in {"text_to_text", "text_to_image"}:
                raise ValueError(f"case_id={case_id} invalid test_type={test_type}")
            if not query:
                raise ValueError(f"case_id={case_id} query is empty")
            if not isinstance(candidates, list) or not candidates:
                raise ValueError(f"case_id={case_id} candidates is empty")

            # Query embedding
            q_embed = provider.embed_text(query)
            q_vec = q_embed["vector"]
            q_ok, q_reason = is_valid_vector(q_vec, expected_dim)
            if not q_ok:
                raise RuntimeError(f"case_id={case_id} query vector invalid: {q_reason}")

            # Candidate ranking and per-case vector checks
            ranking, vec_stats = rank_candidates(provider, q_vec, candidates, expected_dim)
            quality = evaluate_case_ranking(ranking)

            # Aggregate
            total_top1 += float(quality["top1_hit"])
            total_mrr += float(quality["mrr"])
            total_valid_vectors += 1 + int(vec_stats["candidate_vector_valid"])  # +1 query vector
            total_invalid_vectors += int(vec_stats["candidate_vector_invalid"])
            case_latency = float(q_embed.get("latency_ms", 0.0)) + float(vec_stats["candidate_embed_latency_ms_sum"])
            total_latency_ms += case_latency

            case_result = {
                "case_id": case_id,
                "test_type": test_type,
                "query": query,
                "query_embed_latency_ms": round(float(q_embed.get("latency_ms", 0.0)), 3),
                "case_total_latency_ms": round(case_latency, 3),
                "quality": quality,
                "vector_stats": {
                    "query_vector_valid": q_ok,
                    "candidate_vector_valid_count": vec_stats["candidate_vector_valid"],
                    "candidate_vector_invalid_count": vec_stats["candidate_vector_invalid"],
                    "candidate_vector_invalid_details": vec_stats["candidate_vector_invalid_details"],
                },
                "ranking": ranking,
            }
            results.append(case_result)
            LOGGER.info(
                "Case %s done: top1_hit=%.2f mrr=%.3f latency_ms=%.2f",
                case_id,
                quality["top1_hit"],
                quality["mrr"],
                case_latency,
            )
        except Exception as exc:  # pylint: disable=broad-except
            err_msg = f"case_id={case_id} failed: {exc}"
            case_errors.append(err_msg)
            LOGGER.error(err_msg)
            LOGGER.debug(traceback.format_exc())

    n = len(results)
    overall = {
        "provider": provider.name(),
        "case_count": len(tests),
        "case_success_count": n,
        "case_error_count": len(case_errors),
        "top1_accuracy": round(total_top1 / n, 6) if n > 0 else None,
        "mrr": round(total_mrr / n, 6) if n > 0 else None,
        "vector_valid_rate": round(total_valid_vectors / (total_valid_vectors + total_invalid_vectors), 6)
        if (total_valid_vectors + total_invalid_vectors) > 0
        else None,
        "avg_case_latency_ms": round(total_latency_ms / n, 3) if n > 0 else None,
    }

    return {
        "overall": overall,
        "cases": results,
        "case_errors": case_errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="A/B verify Doubao vs Qwen embedding vectors with dot-product similarity.")
    parser.add_argument("--input", required=True, help="test cases json path")
    parser.add_argument("--output", required=True, help="report output json path")
    parser.add_argument("--dimension", type=int, default=1024, help="embedding vector dimension")
    parser.add_argument("--timeout-ms", type=int, default=30000, help="HTTP timeout in milliseconds")
    parser.add_argument("--max-retries", type=int, default=2, help="retry count for API calls")
    parser.add_argument("--retry-sleep-ms", type=int, default=500, help="retry base sleep milliseconds")
    parser.add_argument("--verbose", action="store_true", help="enable debug logging")

    parser.add_argument("--enable-qwen", action="store_true", default=True, help="enable qwen provider")
    parser.add_argument("--enable-doubao", action="store_true", default=True, help="enable doubao provider")
    parser.add_argument("--disable-qwen", action="store_true", help="disable qwen provider")
    parser.add_argument("--disable-doubao", action="store_true", help="disable doubao provider")

    parser.add_argument("--qwen-model", default="qwen3-vl-embedding", help="qwen model id")
    parser.add_argument("--doubao-model", default="doubao-embedding-vision-250615", help="doubao model id")
    parser.add_argument("--qwen-api-key", default="", help="qwen api key; if empty, read DASHSCOPE_API_KEY")
    parser.add_argument("--doubao-api-key", default="", help="doubao api key; if empty, read ARK_API_KEY")
    args = parser.parse_args()

    setup_logging(args.verbose)

    try:
        if args.disable_qwen:
            args.enable_qwen = False
        if args.disable_doubao:
            args.enable_doubao = False

        # API key fallback from environment.
        if not args.qwen_api_key:
            args.qwen_api_key = str(os.environ.get("DASHSCOPE_API_KEY", "")).strip()
        if not args.doubao_api_key:
            args.doubao_api_key = str(os.environ.get("ARK_API_KEY", "")).strip()

        input_path = Path(args.input).expanduser().resolve()
        output_path = Path(args.output).expanduser().resolve()
        payload = load_json(input_path)

        tests = payload.get("tests", [])
        if not isinstance(tests, list) or not tests:
            raise ValueError("input.tests must be non-empty list")

        providers = build_providers(args)

        report: Dict[str, Any] = {
            "meta": {
                "input": str(input_path),
                "dimension": args.dimension,
                "case_count": len(tests),
                "providers_requested": [p.name() for p in providers],
                "generated_at": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
            },
            "providers": {},
            "errors": [],
        }

        for provider in providers:
            try:
                report["providers"][provider.name()] = run_provider_eval(provider, tests, args.dimension)
            except Exception as exc:  # pylint: disable=broad-except
                err_msg = f"provider={provider.name()} failed: {exc}"
                LOGGER.error(err_msg)
                LOGGER.debug(traceback.format_exc())
                report["errors"].append(err_msg)

        save_json(output_path, report)
        LOGGER.info("Report written to %s", output_path)
        LOGGER.info("Done.")
        return 0
    except Exception as exc:  # pylint: disable=broad-except
        LOGGER.error("Fatal error: %s", exc)
        LOGGER.debug(traceback.format_exc())
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
