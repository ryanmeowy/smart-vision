from __future__ import annotations

import base64
import html
import hashlib
import json
import os
import re
import time
import uuid
from typing import Any

import requests
import streamlit as st

REQUEST_TIMEOUT_SECONDS = 20
ACTION_STALE_TIMEOUT_SECONDS = 45
SUCCESS_CODE = 200
MAX_IMAGE_UPLOAD_BYTES = 10 * 1024 * 1024
RESULT_STATE_KEYS = {
    "text": "result_state_text",
    "image": "result_state_image",
    "similar": "result_state_similar",
}
LAYOUT_OPTIONS = ["Large (2 per row)", "Compact (3 per row)"]
OCR_PREVIEW_MAX_CHARS = 100
HIGHLIGHT_PREVIEW_MAX_CHARS = 80
SPO_PREVIEW_MAX_ITEMS = 3
SPO_PREVIEW_MAX_CHARS = 140


def render_text_search_page(base_url: str) -> None:
    st.subheader("Text Search")
    st.caption("Endpoint: POST /api/v1/vision/search")

    state = _get_result_state("text")
    keyword_store_key = "text_search_keyword_store"
    keyword_widget_key = "text_search_keyword_widget"
    strategy_store_key = "text_search_strategy_store"
    strategy_widget_key = "text_search_strategy_widget"
    topk_store_key = "text_search_topk_store"
    topk_widget_key = "text_search_topk_widget"
    limit_store_key = "text_search_limit_store"
    limit_widget_key = "text_search_limit_widget"
    ocr_store_key = "text_search_enable_ocr_store"
    ocr_widget_key = "text_search_enable_ocr_widget"
    if keyword_store_key not in st.session_state:
        st.session_state[keyword_store_key] = ""
    if keyword_widget_key not in st.session_state:
        st.session_state[keyword_widget_key] = st.session_state[keyword_store_key]
    if strategy_store_key not in st.session_state:
        st.session_state[strategy_store_key] = "0"
    if strategy_widget_key not in st.session_state:
        st.session_state[strategy_widget_key] = st.session_state[strategy_store_key]
    if topk_store_key not in st.session_state:
        st.session_state[topk_store_key] = 30
    if topk_widget_key not in st.session_state:
        st.session_state[topk_widget_key] = int(st.session_state[topk_store_key])
    if limit_store_key not in st.session_state:
        st.session_state[limit_store_key] = 20
    if limit_widget_key not in st.session_state:
        st.session_state[limit_widget_key] = int(st.session_state[limit_store_key])
    if ocr_store_key not in st.session_state:
        st.session_state[ocr_store_key] = True
    if ocr_widget_key not in st.session_state:
        st.session_state[ocr_widget_key] = bool(st.session_state[ocr_store_key])

    with st.form("text_search_form"):
        row1_col1, row1_col2 = st.columns([5, 2])
        keyword = row1_col1.text_input(
            "Keyword",
            placeholder="e.g. 猫咪在沙发上",
            key=keyword_widget_key,
        )
        strategy = row1_col2.selectbox(
            "Strategy",
            options=["0", "1", "2"],
            help="0: hybrid, 1: vector, 2: text",
            key=strategy_widget_key,
        )
        row2_col1, row2_col2 = st.columns([2, 2])
        top_k = row2_col1.number_input("TopK", min_value=1, max_value=200, key=topk_widget_key)
        limit = row2_col2.number_input("Limit", min_value=1, max_value=100, key=limit_widget_key)
        enable_ocr = st.checkbox("Enable OCR", key=ocr_widget_key)
        draft_payload = {
            "keyword": keyword.strip(),
            "searchType": str(strategy),
            "limit": int(limit),
            "topK": int(top_k),
            "enableOcr": bool(enable_ocr),
        }
        submitted = st.form_submit_button(
            "Run Search",
            type="primary",
            disabled=_is_action_busy("text_search", draft_payload),
        )
    st.session_state[keyword_store_key] = keyword
    st.session_state[strategy_store_key] = str(strategy)
    st.session_state[topk_store_key] = int(top_k)
    st.session_state[limit_store_key] = int(limit)
    st.session_state[ocr_store_key] = bool(enable_ocr)

    if submitted:
        if not keyword.strip():
            st.warning("Keyword is required.")
            return

        payload = {
            "keyword": keyword.strip(),
            "searchType": str(strategy),
            "limit": int(limit),
            "topK": int(top_k),
            "enableOcr": bool(enable_ocr),
        }
        _queue_action("text_search", payload)
        st.rerun()

    if _consume_queued_action("text_search", draft_payload):
        try:
            with st.spinner("Searching..."):
                response_payload = _request_json(
                    method="POST",
                    url=f"{_normalize_base_url(base_url)}/api/v1/vision/search",
                    json=draft_payload,
                )
            if response_payload is None:
                return
            data, headers, status_code = response_payload

            state["results"] = data
            state["headers"] = headers
            state["status_code"] = status_code
            state["payload_text"] = _pretty_json(draft_payload)
            state["keyword"] = draft_payload.get("keyword", "")
            state["has_searched"] = True
        finally:
            _complete_action("text_search")
            st.rerun()

    if state["has_searched"]:
        _render_response_meta(state["status_code"], state["headers"])
        if state["payload_text"]:
            st.code(state["payload_text"], language="json")
        _render_search_results(state["results"], layout_key="text", highlight_term=_safe_str(state.get("keyword")))


def render_image_search_page(base_url: str) -> None:
    st.subheader("Search By Image")
    st.caption("Endpoint: POST /api/v1/vision/search-by-image")
    st.caption("Image limit: up to 10MB per file. Backend will apply compression for embedding when needed.")

    state = _get_result_state("image")
    limit_store_key = "image_search_limit_store"
    limit_widget_key = "image_search_limit_widget"
    if limit_store_key not in st.session_state:
        st.session_state[limit_store_key] = 20
    if limit_widget_key not in st.session_state:
        st.session_state[limit_widget_key] = int(st.session_state[limit_store_key])

    uploaded = st.file_uploader(
        "Upload image",
        type=["png", "jpg", "jpeg", "webp"],
        accept_multiple_files=False,
        key="image_search_upload_widget",
    )
    limit = st.number_input("Limit", min_value=1, max_value=100, key=limit_widget_key)
    draft_payload = {
        "limit": int(limit),
        "file": _uploaded_file_fingerprint(uploaded),
    }
    submitted = st.button(
        "Run Image Search",
        type="primary",
        disabled=_is_action_busy("image_search", draft_payload),
    )
    st.session_state[limit_store_key] = int(limit)

    if submitted:
        if uploaded is None:
            st.warning("Please upload an image file first.")
            return
        if not _validate_uploaded_image_size(uploaded, "Uploaded image"):
            return
        _queue_action("image_search", draft_payload)
        st.rerun()

    if _consume_queued_action("image_search", draft_payload):
        try:
            with st.spinner("Searching by image..."):
                response_payload = _request_json(
                    method="POST",
                    url=f"{_normalize_base_url(base_url)}/api/v1/vision/search-by-image",
                    params={"limit": int(limit)},
                    files={"file": (uploaded.name, uploaded.getvalue(), uploaded.type or "application/octet-stream")},
                )
            if response_payload is None:
                return
            data, headers, status_code = response_payload

            state["results"] = data
            state["headers"] = headers
            state["status_code"] = status_code
            state["payload_text"] = (
                f"POST {_normalize_base_url(base_url)}/api/v1/vision/search-by-image?limit={int(limit)}"
            )
            state["has_searched"] = True
        finally:
            _complete_action("image_search")
            st.rerun()

    if state["has_searched"]:
        _render_response_meta(state["status_code"], state["headers"])
        if state["payload_text"]:
            st.code(state["payload_text"], language="bash")
        _render_search_results(state["results"], layout_key="image")


def render_image_analyze_page(base_url: str) -> None:
    st.subheader("Image Analyze")
    st.caption("Endpoint: POST /api/v1/vision/analyze/stream")
    st.info("Upload one image and stream full analysis: summary, tags, OCR, graph, and search suggestions.")
    st.caption("Image limit: up to 10MB per file. Backend will apply compression for embedding when needed.")

    analyze_state_key = "image_analyze_state"
    enable_ocr_store_key = "image_analyze_enable_ocr_store"
    enable_ocr_widget_key = "image_analyze_enable_ocr_widget"
    enable_graph_store_key = "image_analyze_enable_graph_store"
    enable_graph_widget_key = "image_analyze_enable_graph_widget"
    if enable_ocr_store_key not in st.session_state:
        st.session_state[enable_ocr_store_key] = True
    if enable_ocr_widget_key not in st.session_state:
        st.session_state[enable_ocr_widget_key] = bool(st.session_state[enable_ocr_store_key])
    if enable_graph_store_key not in st.session_state:
        st.session_state[enable_graph_store_key] = True
    if enable_graph_widget_key not in st.session_state:
        st.session_state[enable_graph_widget_key] = bool(st.session_state[enable_graph_store_key])
    if analyze_state_key not in st.session_state or not isinstance(st.session_state[analyze_state_key], dict):
        st.session_state[analyze_state_key] = {
            "has_result": False,
            "image_name": "",
            "image_bytes": None,
            "meta": {},
            "summary": "",
            "tags": [],
            "ocr": "",
            "graph": [],
            "suggestions": [],
            "status": "",
        }
    analyze_state = st.session_state[analyze_state_key]

    uploaded = st.file_uploader(
        "Upload image",
        type=["png", "jpg", "jpeg", "webp"],
        accept_multiple_files=False,
        key="image_analyze_upload_widget",
    )
    col1, col2 = st.columns(2)
    enable_ocr = col1.checkbox("Enable OCR", key=enable_ocr_widget_key)
    enable_graph = col2.checkbox("Enable Graph (SPO)", key=enable_graph_widget_key)
    analyze_payload = {
        "file": _uploaded_file_fingerprint(uploaded),
        "enableOcr": bool(enable_ocr),
        "enableGraph": bool(enable_graph),
    }
    submitted = st.button(
        "Run Analyze",
        type="primary",
        disabled=_is_action_busy("image_analyze", analyze_payload),
    )
    st.session_state[enable_ocr_store_key] = bool(enable_ocr)
    st.session_state[enable_graph_store_key] = bool(enable_graph)

    if submitted:
        if uploaded is None:
            st.warning("Please upload an image file first.")
            return
        if not _validate_uploaded_image_size(uploaded, "Uploaded image"):
            return
        _queue_action("image_analyze", analyze_payload)
        st.rerun()

    if _consume_queued_action("image_analyze", analyze_payload):
        if uploaded is None:
            _complete_action("image_analyze")
            st.rerun()
            return
        try:
            st.markdown("### Uploaded Image")
            st.image(uploaded.getvalue(), caption=uploaded.name, width=320)

            st.markdown("### Streaming Analysis")
            st.markdown("**Meta**")
            meta_box = st.empty()
            st.markdown("**Summary**")
            summary_box = st.empty()
            st.markdown("**Tags**")
            tags_box = st.empty()
            st.markdown("**OCR Text**")
            ocr_box = st.empty()
            st.markdown("**Graph Triples**")
            graph_box = st.empty()
            st.markdown("**Search Suggestions**")
            suggestion_box = st.empty()
            status_box = st.empty()

            summary_text = ""
            ocr_text = ""
            tags: list[str] = []
            graph_items: list[dict[str, Any]] = []
            suggestions: list[str] = []
            meta_payload: dict[str, Any] = {}
            final_status = ""

            try:
                response = requests.post(
                    f"{_normalize_base_url(base_url)}/api/v1/vision/analyze/stream",
                    files={"file": (uploaded.name, uploaded.getvalue(), uploaded.type or "application/octet-stream")},
                    data={
                        "mode": "general",
                        "enableOcr": str(bool(enable_ocr)).lower(),
                        "enableGraph": str(bool(enable_graph)).lower(),
                    },
                    stream=True,
                    timeout=(5, 180),
                )
            except requests.exceptions.RequestException as exc:
                st.error(f"Failed to call /vision/analyze/stream: {exc}")
                return

            if response.status_code >= 400:
                st.error(f"HTTP {response.status_code}: {response.text[:600]}")
                return

            response.encoding = "utf-8"

            for event_name, payload in _iter_sse_events(response):
                if event_name == "meta":
                    if isinstance(payload, dict):
                        meta_payload = payload
                    meta_box.json(payload)
                    continue
                if event_name == "summary":
                    delta = _safe_str(payload.get("delta")) if isinstance(payload, dict) else ""
                    summary_text = _render_typewriter_delta(summary_box, summary_text, delta)
                    continue
                if event_name == "summary_end":
                    final_text = _safe_str(payload.get("text")) if isinstance(payload, dict) else ""
                    if final_text:
                        summary_text = final_text
                    summary_box.write(summary_text if summary_text else "-")
                    continue
                if event_name == "tags":
                    items = payload.get("items") if isinstance(payload, dict) else None
                    if isinstance(items, list):
                        tags = [str(x).strip() for x in items if str(x).strip()]
                    tags_box.write(tags if tags else [])
                    continue
                if event_name == "ocr":
                    delta = _safe_str(payload.get("delta")) if isinstance(payload, dict) else ""
                    ocr_text += delta
                    ocr_box.code(ocr_text if ocr_text else "-", language="text")
                    continue
                if event_name == "ocr_end":
                    final_ocr = _safe_str(payload.get("text")) if isinstance(payload, dict) else ""
                    if final_ocr:
                        ocr_text = final_ocr
                    ocr_box.code(ocr_text if ocr_text else "-", language="text")
                    continue
                if event_name == "graph":
                    items = payload.get("items") if isinstance(payload, dict) else None
                    if isinstance(items, list):
                        graph_items = [x for x in items if isinstance(x, dict)]
                    if graph_items:
                        graph_box.dataframe(graph_items, use_container_width=True)
                    else:
                        graph_box.info("No graph triples.")
                    continue
                if event_name == "suggestions":
                    items = payload.get("items") if isinstance(payload, dict) else None
                    if isinstance(items, list):
                        suggestions = [str(x).strip() for x in items if str(x).strip()]
                    suggestion_box.write(suggestions if suggestions else [])
                    continue
                if event_name == "error":
                    message = _safe_str(payload.get("message")) if isinstance(payload, dict) else "Analyze failed."
                    final_status = message or "Analyze failed."
                    status_box.error(final_status)
                    continue
                if event_name == "done":
                    ok = bool(payload.get("ok")) if isinstance(payload, dict) else False
                    final_status = "Analyze completed." if ok else "Analyze finished with errors."
                    if ok:
                        status_box.success(final_status)
                    else:
                        status_box.warning(final_status)

            analyze_state["has_result"] = True
            analyze_state["image_name"] = uploaded.name
            analyze_state["image_bytes"] = uploaded.getvalue()
            analyze_state["meta"] = meta_payload
            analyze_state["summary"] = summary_text
            analyze_state["tags"] = tags
            analyze_state["ocr"] = ocr_text
            analyze_state["graph"] = graph_items
            analyze_state["suggestions"] = suggestions
            analyze_state["status"] = final_status
        finally:
            _complete_action("image_analyze")
            st.rerun()
        return

    if analyze_state.get("has_result"):
        st.markdown("### Uploaded Image")
        image_bytes = analyze_state.get("image_bytes")
        if image_bytes:
            st.image(image_bytes, caption=str(analyze_state.get("image_name", "")), width=320)

        st.markdown("### Analysis Result")
        st.markdown("**Meta**")
        st.json(analyze_state.get("meta", {}))
        st.markdown("**Summary**")
        st.write(str(analyze_state.get("summary", "")) or "-")
        st.markdown("**Tags**")
        st.write(analyze_state.get("tags", []))
        st.markdown("**OCR Text**")
        st.code(str(analyze_state.get("ocr", "")) or "-", language="text")
        st.markdown("**Graph Triples**")
        graph_items = analyze_state.get("graph", [])
        if isinstance(graph_items, list) and graph_items:
            st.dataframe(graph_items, use_container_width=True)
        else:
            st.info("No graph triples.")
        st.markdown("**Search Suggestions**")
        st.write(analyze_state.get("suggestions", []))
        status_text = str(analyze_state.get("status", ""))
        if status_text:
            st.success(status_text)


def render_vector_compare_page(base_url: str) -> None:
    st.subheader("Vector Compare")
    st.caption("Endpoint: POST /api/v1/vision/vector-compare")
    st.info("Compare semantic vectors for text-text, image-image, or image-text pairs.")
    st.caption("Image limit: up to 10MB per file. Backend will apply compression for embedding when needed.")

    left_type_store_key = "vector_left_type_store"
    left_type_widget_key = "vector_left_type_widget"
    right_type_store_key = "vector_right_type_store"
    right_type_widget_key = "vector_right_type_widget"
    left_text_store_key = "vector_left_text_store"
    left_text_widget_key = "vector_left_text_widget"
    right_text_store_key = "vector_right_text_store"
    right_text_widget_key = "vector_right_text_widget"
    if left_type_store_key not in st.session_state:
        st.session_state[left_type_store_key] = "text"
    if left_type_widget_key not in st.session_state:
        st.session_state[left_type_widget_key] = st.session_state[left_type_store_key]
    if right_type_store_key not in st.session_state:
        st.session_state[right_type_store_key] = "text"
    if right_type_widget_key not in st.session_state:
        st.session_state[right_type_widget_key] = st.session_state[right_type_store_key]
    if left_text_store_key not in st.session_state:
        st.session_state[left_text_store_key] = ""
    if left_text_widget_key not in st.session_state:
        st.session_state[left_text_widget_key] = st.session_state[left_text_store_key]
    if right_text_store_key not in st.session_state:
        st.session_state[right_text_store_key] = ""
    if right_text_widget_key not in st.session_state:
        st.session_state[right_text_widget_key] = st.session_state[right_text_store_key]

    left_col, right_col = st.columns(2)
    left_type = left_col.selectbox("Left Input Type", options=["text", "image"], key=left_type_widget_key)
    right_type = right_col.selectbox("Right Input Type", options=["text", "image"], key=right_type_widget_key)

    left_text = ""
    right_text = ""
    left_file = None
    right_file = None

    def _vector_compare_file_signature(uploaded: Any) -> dict[str, Any]:
        if uploaded is None:
            return {}
        file_bytes = uploaded.getvalue()
        return {
            "name": str(getattr(uploaded, "name", "")),
            "size": int(getattr(uploaded, "size", 0) or 0),
            "type": str(getattr(uploaded, "type", "")),
            "sha256": hashlib.sha256(file_bytes).hexdigest(),
        }

    if left_type == "text":
        left_text = st.text_area(
            "Left Text",
            placeholder="Input text for semantic compare...",
            height=110,
            key=left_text_widget_key,
        )
    else:
        left_file = st.file_uploader(
            "Left Image",
            type=["png", "jpg", "jpeg", "webp"],
            accept_multiple_files=False,
            key="vector_left_file_widget",
        )

    if right_type == "text":
        right_text = st.text_area(
            "Right Text",
            placeholder="Input text for semantic compare...",
            height=110,
            key=right_text_widget_key,
        )
    else:
        right_file = st.file_uploader(
            "Right Image",
            type=["png", "jpg", "jpeg", "webp"],
            accept_multiple_files=False,
            key="vector_right_file_widget",
        )
    st.session_state[left_type_store_key] = left_type
    st.session_state[right_type_store_key] = right_type
    if left_type == "text":
        st.session_state[left_text_store_key] = left_text
    if right_type == "text":
        st.session_state[right_text_store_key] = right_text

    compare_payload = {
        "leftType": left_type,
        "rightType": right_type,
        "leftText": left_text.strip() if left_type == "text" else "",
        "rightText": right_text.strip() if right_type == "text" else "",
        "leftFile": _vector_compare_file_signature(left_file),
        "rightFile": _vector_compare_file_signature(right_file),
    }
    submitted = st.button(
        "Run Vector Compare",
        type="primary",
        disabled=_is_action_busy("vector_compare", compare_payload),
    )

    compare_state_key = "vector_compare_state"
    if compare_state_key not in st.session_state or not isinstance(st.session_state[compare_state_key], dict):
        st.session_state[compare_state_key] = {
            "has_result": False,
            "request_data": {},
            "left_type": "text",
            "right_type": "text",
            "left_text": "",
            "right_text": "",
            "left_image_name": "",
            "right_image_name": "",
            "left_image_bytes": None,
            "right_image_bytes": None,
            "result": {},
            "status_code": 200,
        }
    compare_state = st.session_state[compare_state_key]

    if not submitted:
        if compare_state.get("has_result"):
            _render_vector_compare_result(compare_state)
    else:
        if left_type == "text" and not left_text.strip():
            st.warning("Left text is required.")
            return
        if right_type == "text" and not right_text.strip():
            st.warning("Right text is required.")
            return
        if left_type == "image" and left_file is None:
            st.warning("Please upload the left image.")
            return
        if right_type == "image" and right_file is None:
            st.warning("Please upload the right image.")
            return
        if left_type == "image" and not _validate_uploaded_image_size(left_file, "Left image"):
            return
        if right_type == "image" and not _validate_uploaded_image_size(right_file, "Right image"):
            return
        _queue_action("vector_compare", compare_payload)
        st.rerun()

    request_data = {
        "leftType": left_type,
        "rightType": right_type,
    }
    if left_type == "text":
        request_data["leftText"] = left_text.strip()
    if right_type == "text":
        request_data["rightText"] = right_text.strip()

    multipart_parts: dict[str, Any] = {
        "leftType": (None, left_type),
        "rightType": (None, right_type),
    }
    if left_type == "image" and left_file is not None:
        multipart_parts["leftFile"] = (
            left_file.name,
            left_file.getvalue(),
            left_file.type or "application/octet-stream",
        )
    elif left_type == "text":
        multipart_parts["leftText"] = (None, left_text.strip())
    if right_type == "image" and right_file is not None:
        multipart_parts["rightFile"] = (
            right_file.name,
            right_file.getvalue(),
            right_file.type or "application/octet-stream",
        )
    elif right_type == "text":
        multipart_parts["rightText"] = (None, right_text.strip())

    if _consume_queued_action("vector_compare", compare_payload):
        try:
            try:
                response = requests.post(
                    f"{_normalize_base_url(base_url)}/api/v1/vision/vector-compare",
                    files=multipart_parts,
                    timeout=REQUEST_TIMEOUT_SECONDS,
                )
            except requests.exceptions.Timeout:
                st.error("Request timed out. Please retry later.")
                return
            except requests.exceptions.RequestException as exc:
                st.error(f"Request failed: {exc}")
                return

            status_code = response.status_code
            st.caption(f"HTTP {status_code}")
            if response.status_code >= 400:
                st.error(f"HTTP {response.status_code}: {response.text[:600]}")
                return

            try:
                envelope = response.json()
            except ValueError:
                st.error("Backend returned non-JSON response.")
                return

            if not isinstance(envelope, dict):
                st.error("Unexpected response shape.")
                return

            if envelope.get("code") != SUCCESS_CODE:
                st.error(f"Business error {envelope.get('code')}: {envelope.get('message', 'Unknown error')}")
                return

            result = envelope.get("data")
            if not isinstance(result, dict):
                st.error("Unexpected response shape: data is not an object.")
                return

            compare_state["has_result"] = True
            compare_state["request_data"] = dict(request_data)
            compare_state["left_type"] = left_type
            compare_state["right_type"] = right_type
            compare_state["left_text"] = left_text.strip()
            compare_state["right_text"] = right_text.strip()
            compare_state["left_image_name"] = left_file.name if left_file is not None else ""
            compare_state["right_image_name"] = right_file.name if right_file is not None else ""
            compare_state["left_image_bytes"] = left_file.getvalue() if left_file is not None else None
            compare_state["right_image_bytes"] = right_file.getvalue() if right_file is not None else None
            compare_state["result"] = result
            compare_state["status_code"] = status_code
        finally:
            _complete_action("vector_compare")
            st.rerun()


def _render_vector_compare_result(compare_state: dict[str, Any]) -> None:
    status_code = int(compare_state.get("status_code", 200) or 200)
    st.caption(f"HTTP {status_code}")
    result = compare_state.get("result")
    if not isinstance(result, dict):
        return

    st.markdown("### Compare Result")
    metric_col1, metric_col2, metric_col3 = st.columns(3)
    metric_col1.metric("Cosine Similarity", str(result.get("cosineSimilarity", "-")))
    score_percent = result.get("scorePercent")
    metric_col2.metric("Score Percent", "-" if score_percent is None else f"{score_percent}%")
    metric_col3.metric("Match Level", str(result.get("matchLevel", "-")))

    st.markdown("### Input Preview")
    preview_col1, preview_col2 = st.columns(2)
    left_type = str(compare_state.get("left_type", "text"))
    right_type = str(compare_state.get("right_type", "text"))
    if left_type == "text":
        preview_col1.text_area("Left Value", value=str(compare_state.get("left_text", "")), height=120, disabled=True)
    else:
        left_image_bytes = compare_state.get("left_image_bytes")
        left_image_name = str(compare_state.get("left_image_name", "left_image"))
        if left_image_bytes:
            preview_col1.image(left_image_bytes, caption=left_image_name, width=320)
    if right_type == "text":
        preview_col2.text_area("Right Value", value=str(compare_state.get("right_text", "")), height=120, disabled=True)
    else:
        right_image_bytes = compare_state.get("right_image_bytes")
        right_image_name = str(compare_state.get("right_image_name", "right_image"))
        if right_image_bytes:
            preview_col2.image(right_image_bytes, caption=right_image_name, width=320)

    st.markdown("### Meta")
    st.json(result)

    if st.session_state.get("show_debug_panel", True):
        st.markdown("### Debug Request Payload")
        safe_payload = dict(compare_state.get("request_data", {}))
        if "leftText" in safe_payload:
            safe_payload["leftText"] = _clip_text(str(safe_payload["leftText"]), 200)
        if "rightText" in safe_payload:
            safe_payload["rightText"] = _clip_text(str(safe_payload["rightText"]), 200)
        st.code(_pretty_json(safe_payload), language="json")


def render_similar_search_page(base_url: str) -> None:
    st.subheader("Similar Search")
    st.caption("Endpoint: GET /api/v1/vision/similar")

    state = _get_result_state("similar")
    image_id_store_key = "similar_search_image_id_store"
    image_id_widget_key = "similar_search_image_id_widget"
    if image_id_store_key not in st.session_state:
        st.session_state[image_id_store_key] = ""
    if image_id_widget_key not in st.session_state:
        st.session_state[image_id_widget_key] = st.session_state[image_id_store_key]

    with st.form("similar_search_form"):
        image_id = st.text_input("Image ID", placeholder="e.g. 1234567890", key=image_id_widget_key)
        draft_payload = {"imageId": image_id.strip()}
        submitted = st.form_submit_button(
            "Run Similar Search",
            type="primary",
            disabled=_is_action_busy("similar_search", draft_payload),
        )
    st.session_state[image_id_store_key] = image_id

    if submitted:
        if not image_id.strip():
            st.warning("Image ID is required.")
            return
        _queue_action("similar_search", draft_payload)
        st.rerun()

    if _consume_queued_action("similar_search", draft_payload):
        try:
            with st.spinner("Searching similar images..."):
                response_payload = _request_json(
                    method="GET",
                    url=f"{_normalize_base_url(base_url)}/api/v1/vision/similar",
                    params={"id": image_id.strip()},
                )
            if response_payload is None:
                return
            data, headers, status_code = response_payload

            state["results"] = data
            state["headers"] = headers
            state["status_code"] = status_code
            state["payload_text"] = f"GET {_normalize_base_url(base_url)}/api/v1/vision/similar?id={image_id.strip()}"
            state["has_searched"] = True
        finally:
            _complete_action("similar_search")
            st.rerun()

    if state["has_searched"]:
        _render_response_meta(state["status_code"], state["headers"])
        if state["payload_text"]:
            st.code(state["payload_text"], language="bash")
        _render_search_results(state["results"], layout_key="similar")


def render_hot_words_page(base_url: str) -> None:
    st.subheader("Hot Words")
    st.caption("Endpoint: GET /api/v1/vision/hot-words")

    if "hot_words_state" not in st.session_state or not isinstance(st.session_state["hot_words_state"], dict):
        st.session_state["hot_words_state"] = {"has_loaded": False, "status_code": 200, "words": []}

    payload = {"endpoint": "hot_words"}
    if st.button("Fetch Hot Words", type="primary", disabled=_is_action_busy("hot_words", payload)):
        _queue_action("hot_words", payload)
        st.rerun()

    if _consume_queued_action("hot_words", payload):
        try:
            with st.spinner("Loading hot words..."):
                response_payload = _request_json(
                    method="GET",
                    url=f"{_normalize_base_url(base_url)}/api/v1/vision/hot-words",
                )
            if response_payload is None:
                return
            data, _, status_code = response_payload
            words = data if isinstance(data, list) else []
            st.session_state["hot_words_state"] = {
                "has_loaded": True,
                "status_code": status_code,
                "words": words,
            }
        finally:
            _complete_action("hot_words")
            st.rerun()

    hot_words_state = st.session_state["hot_words_state"]
    if hot_words_state.get("has_loaded"):
        st.caption(f"HTTP {hot_words_state.get('status_code', 200)}")
        words = hot_words_state.get("words")
        if isinstance(words, list) and words:
            st.markdown("### Hot Words")
            st.write(words)
        else:
            st.info("No hot words currently.")


def render_auth_admin_page(base_url: str) -> None:
    st.subheader("Auth Admin")
    st.caption("Endpoints: GET /api/v1/auth/refresh-token, GET /api/v1/auth/clean-token")
    st.warning("Admin operation: keep `X-Admin-Secret` private and do not expose in shared demos.")

    admin_secret_store_key = "auth_admin_secret_store"
    admin_secret_widget_key = "auth_admin_secret_widget"
    refresh_code_store_key = "auth_refresh_code_store"
    refresh_code_widget_key = "auth_refresh_code_widget"
    if admin_secret_store_key not in st.session_state:
        st.session_state[admin_secret_store_key] = ""
    if admin_secret_widget_key not in st.session_state:
        st.session_state[admin_secret_widget_key] = st.session_state[admin_secret_store_key]
    if refresh_code_store_key not in st.session_state:
        st.session_state[refresh_code_store_key] = ""
    if refresh_code_widget_key not in st.session_state:
        st.session_state[refresh_code_widget_key] = st.session_state[refresh_code_store_key]

    admin_secret = st.text_input("X-Admin-Secret", type="password", key=admin_secret_widget_key)
    refresh_code = st.text_input("Refresh Code (optional)", key=refresh_code_widget_key)
    st.session_state[admin_secret_store_key] = admin_secret
    st.session_state[refresh_code_store_key] = refresh_code

    col1, col2 = st.columns(2)
    if col1.button("Refresh Token", type="primary"):
        if not admin_secret.strip():
            st.warning("X-Admin-Secret is required.")
        else:
            with st.spinner("Refreshing token..."):
                params = {"code": refresh_code.strip()} if refresh_code.strip() else None
                _request_admin_endpoint(
                    base_url=_normalize_base_url(base_url),
                    path="/api/v1/auth/refresh-token",
                    admin_secret=admin_secret.strip(),
                    params=params,
                )

    if col2.button("Clean Token", type="secondary"):
        if not admin_secret.strip():
            st.warning("X-Admin-Secret is required.")
        else:
            with st.spinner("Cleaning token..."):
                _request_admin_endpoint(
                    base_url=_normalize_base_url(base_url),
                    path="/api/v1/auth/clean-token",
                    admin_secret=admin_secret.strip(),
                    params=None,
                )


def render_image_batch_process_page(base_url: str) -> None:
    st.subheader("Batch Process")
    st.caption("Endpoint: POST /api/v1/image/batch-tasks")
    st.info(
        "Integrated flow: fetch STS -> upload to OSS -> submit async task. "
        "Each image has independent status and can be retried manually when failed."
    )
    st.caption("Image limit: up to 10MB per file.")

    if "batch_task_ids" not in st.session_state:
        st.session_state.batch_task_ids = []
    task_cache_key = "batch_task_cache"
    active_task_ids_key = "batch_active_task_ids"
    if task_cache_key not in st.session_state or not isinstance(st.session_state[task_cache_key], dict):
        st.session_state[task_cache_key] = {}
    if active_task_ids_key not in st.session_state or not isinstance(st.session_state[active_task_ids_key], list):
        st.session_state[active_task_ids_key] = []
    active_statuses = {"PENDING", "RUNNING"}

    env_cfg = _load_env_config()
    token_store_key = "batch_token_store"
    token_widget_key = "batch_token_widget"
    key_prefix_store_key = "batch_key_prefix_store"
    key_prefix_widget_key = "batch_key_prefix_widget"
    bucket_store_key = "batch_bucket_store"
    bucket_widget_key = "batch_bucket_widget"
    endpoint_store_key = "batch_endpoint_store"
    endpoint_widget_key = "batch_endpoint_widget"
    encrypt_key_store_key = "batch_encrypt_key_store"
    encrypt_key_widget_key = "batch_encrypt_key_widget"
    encrypt_iv_store_key = "batch_encrypt_iv_store"
    encrypt_iv_widget_key = "batch_encrypt_iv_widget"
    auto_refresh_store_key = "batch_task_auto_refresh_store"
    auto_refresh_widget_key = "batch_task_auto_refresh_widget"
    if token_store_key not in st.session_state:
        st.session_state[token_store_key] = ""
    if token_widget_key not in st.session_state:
        st.session_state[token_widget_key] = st.session_state[token_store_key]
    if key_prefix_store_key not in st.session_state:
        st.session_state[key_prefix_store_key] = ""
    if key_prefix_widget_key not in st.session_state:
        st.session_state[key_prefix_widget_key] = st.session_state[key_prefix_store_key]
    if bucket_store_key not in st.session_state:
        st.session_state[bucket_store_key] = env_cfg.get("ALIYUN_OSS_BUCKET_NAME", "")
    if bucket_widget_key not in st.session_state:
        st.session_state[bucket_widget_key] = st.session_state[bucket_store_key]
    if endpoint_store_key not in st.session_state:
        st.session_state[endpoint_store_key] = env_cfg.get("OSS_ENDPOINT", "")
    if endpoint_widget_key not in st.session_state:
        st.session_state[endpoint_widget_key] = st.session_state[endpoint_store_key]
    if encrypt_key_store_key not in st.session_state:
        st.session_state[encrypt_key_store_key] = env_cfg.get("APP_ENCRYPT_KEY", "")
    if encrypt_key_widget_key not in st.session_state:
        st.session_state[encrypt_key_widget_key] = st.session_state[encrypt_key_store_key]
    if encrypt_iv_store_key not in st.session_state:
        st.session_state[encrypt_iv_store_key] = env_cfg.get("APP_ENCRYPT_IV", "")
    if encrypt_iv_widget_key not in st.session_state:
        st.session_state[encrypt_iv_widget_key] = st.session_state[encrypt_iv_store_key]
    if auto_refresh_store_key not in st.session_state:
        st.session_state[auto_refresh_store_key] = False
    if auto_refresh_widget_key not in st.session_state:
        st.session_state[auto_refresh_widget_key] = bool(st.session_state[auto_refresh_store_key])

    token = st.text_input("X-Access-Token", type="password", help="Required header for protected APIs", key=token_widget_key)
    key_prefix = st.text_input(
        "OSS Key Prefix",
        placeholder="Input manually, e.g. images/ui-upload/",
        key=key_prefix_widget_key,
    )
    files = st.file_uploader(
        "Select local files for integrated upload",
        type=["png", "jpg", "jpeg", "webp"],
        accept_multiple_files=True,
        key="batch_files_widget",
    )

    bucket_name = st.text_input("OSS Bucket", key=bucket_widget_key)
    oss_endpoint = st.text_input("OSS Endpoint", key=endpoint_widget_key)
    encrypt_key = st.text_input(
        "APP_ENCRYPT_KEY (base64)",
        type="password",
        key=encrypt_key_widget_key,
    )
    encrypt_iv = st.text_input(
        "APP_ENCRYPT_IV (base64)",
        type="password",
        key=encrypt_iv_widget_key,
    )
    st.session_state[token_store_key] = token
    st.session_state[key_prefix_store_key] = key_prefix
    st.session_state[bucket_store_key] = bucket_name
    st.session_state[endpoint_store_key] = oss_endpoint
    st.session_state[encrypt_key_store_key] = encrypt_key
    st.session_state[encrypt_iv_store_key] = encrypt_iv

    if st.button("Upload To OSS And Submit Task", type="primary"):
        if not token.strip():
            st.warning("X-Access-Token is required.")
            return
        if not files:
            st.warning("Please select at least one file.")
            return
        oversize_files = [
            str(file.name)
            for file in files
            if getattr(file, "size", 0) > MAX_IMAGE_UPLOAD_BYTES
        ]
        if oversize_files:
            st.warning(
                "These files exceed 10MB and cannot be uploaded: "
                + ", ".join(oversize_files[:5])
                + (" ..." if len(oversize_files) > 5 else "")
            )
            return
        if not key_prefix.strip():
            st.warning("OSS Key Prefix is required.")
            return
        if not bucket_name.strip() or not oss_endpoint.strip():
            st.warning("OSS bucket and endpoint are required.")
            return
        if not encrypt_key.strip() or not encrypt_iv.strip():
            st.warning("APP_ENCRYPT_KEY and APP_ENCRYPT_IV are required for STS decryption.")
            return
        try:
            with st.spinner("Step 1/3: Fetching and decrypting STS token..."):
                sts = _fetch_and_decrypt_sts(
                    base_url=_normalize_base_url(base_url),
                    access_token=token.strip(),
                    encrypt_key_b64=encrypt_key.strip(),
                    encrypt_iv_b64=encrypt_iv.strip(),
                )
            if sts is None:
                return

            with st.spinner("Step 2/3: Uploading files to OSS..."):
                items = _upload_files_to_oss(
                    files=files,
                    key_prefix=key_prefix,
                    bucket_name=bucket_name.strip(),
                    endpoint=oss_endpoint.strip(),
                    sts=sts,
                )
            if not items:
                return

            st.markdown("### Uploaded Items")
            st.dataframe(items, use_container_width=True)

            with st.spinner("Step 3/3: Submitting async task..."):
                envelope = _submit_batch_task(
                    base_url=_normalize_base_url(base_url),
                    access_token=token.strip(),
                    items=items,
                )
            if envelope is None:
                return

            task = envelope.get("data") if isinstance(envelope, dict) else None
            task_id = task.get("taskId") if isinstance(task, dict) else None
            if isinstance(task_id, str) and task_id:
                if task_id not in st.session_state.batch_task_ids:
                    st.session_state.batch_task_ids.append(task_id)
                task_cache = st.session_state.get(task_cache_key)
                if not isinstance(task_cache, dict):
                    task_cache = {}
                if isinstance(task, dict):
                    task_cache[task_id] = task
                st.session_state[task_cache_key] = task_cache
                active_task_ids = st.session_state.get(active_task_ids_key)
                if not isinstance(active_task_ids, list):
                    active_task_ids = []
                initial_status = str(task.get("status", "PENDING")) if isinstance(task, dict) else "PENDING"
                if initial_status in active_statuses:
                    if task_id not in active_task_ids:
                        active_task_ids.append(task_id)
                else:
                    active_task_ids = [item for item in active_task_ids if item != task_id]
                st.session_state[active_task_ids_key] = active_task_ids
                st.success(f"Task submitted: {task_id}")
            else:
                st.warning("Task submitted, but taskId was not found in response.")
        except Exception as exc:
            st.error(f"Batch submit failed: {exc}")

    dashboard_placeholder = st.empty()
    active_tasks_placeholder = None
    auto_refresh = False
    has_active_task = False
    with dashboard_placeholder.container():
        st.markdown("### Task Dashboard")
        task_ids: list[str] = st.session_state.batch_task_ids
        if not task_ids:
            st.caption("No submitted tasks yet.")
            return

        control_col1, control_col2 = st.columns([1, 2])
        refresh_all = control_col1.button("Refresh Task Status")
        auto_refresh = control_col2.toggle("Auto refresh every 3 seconds", key=auto_refresh_widget_key)
        st.session_state[auto_refresh_store_key] = bool(auto_refresh)

        if not token.strip():
            st.warning("Input X-Access-Token to load task status and retry failed images.")
            return

        base = _normalize_base_url(base_url)
        task_cache = st.session_state.get(task_cache_key)
        if not isinstance(task_cache, dict):
            task_cache = {}
        active_task_ids = st.session_state.get(active_task_ids_key)
        if not isinstance(active_task_ids, list):
            active_task_ids = []
        normalized_task_ids = [str(task_id) for task_id in task_ids]
        active_task_set = {task_id for task_id in active_task_ids if task_id in normalized_task_ids}
        ordered_task_ids = list(reversed(normalized_task_ids))

        query_task_ids: list[str]
        if refresh_all:
            query_task_ids = list(ordered_task_ids)
        else:
            query_task_ids = [task_id for task_id in ordered_task_ids if task_id in active_task_set]
            for task_id in ordered_task_ids:
                if task_id not in task_cache and task_id not in query_task_ids:
                    query_task_ids.append(task_id)

        for task_id in query_task_ids:
            task_payload = _get_batch_task_status(base_url=base, access_token=token.strip(), task_id=task_id)
            if task_payload is None:
                continue
            task_data = task_payload.get("data")
            if not isinstance(task_data, dict):
                st.error(f"Invalid task data shape for task {task_id}")
                continue
            task_cache[task_id] = task_data
            task_status = str(task_data.get("status", ""))
            if task_status in active_statuses:
                active_task_set.add(task_id)
            else:
                active_task_set.discard(task_id)

        st.session_state[task_cache_key] = task_cache
        st.session_state[active_task_ids_key] = [task_id for task_id in normalized_task_ids if task_id in active_task_set]

        active_display_ids = [task_id for task_id in ordered_task_ids if task_id in active_task_set]
        completed_display_ids = [task_id for task_id in ordered_task_ids if task_id not in active_task_set]
        has_active_task = bool(active_display_ids)

        active_tasks_placeholder = st.empty()
        with active_tasks_placeholder.container():
            st.markdown("**Active Tasks**")
            if active_display_ids:
                for task_id in active_display_ids:
                    task_data = task_cache.get(task_id)
                    if not isinstance(task_data, dict):
                        st.caption(f"Task `{task_id}` is active. Waiting for status update...")
                        continue
                    _render_single_batch_task(
                        task_data=task_data,
                        base_url=base,
                        access_token=token.strip(),
                    )
            else:
                st.caption("No active tasks.")

        st.markdown("**Completed / History**")
        if completed_display_ids:
            for task_id in completed_display_ids:
                task_data = task_cache.get(task_id)
                if not isinstance(task_data, dict):
                    st.caption(f"Task `{task_id}` has no cached status yet. Click `Refresh Task Status`.")
                    continue
                _render_single_batch_task(
                    task_data=task_data,
                    base_url=base,
                    access_token=token.strip(),
                )
        else:
            st.caption("No completed tasks yet.")

    if auto_refresh and has_active_task:
        if active_tasks_placeholder is not None:
            active_tasks_placeholder.empty()
        time.sleep(3)
        st.rerun()


def _render_single_batch_task(*, task_data: dict[str, Any], base_url: str, access_token: str) -> None:
    task_id = str(task_data.get("taskId", ""))
    status = str(task_data.get("status", "UNKNOWN"))
    total = int(task_data.get("total", 0) or 0)
    success_count = int(task_data.get("successCount", 0) or 0)
    failure_count = int(task_data.get("failureCount", 0) or 0)
    running_count = int(task_data.get("runningCount", 0) or 0)
    pending_count = int(task_data.get("pendingCount", 0) or 0)
    done = success_count + failure_count
    progress = (float(done) / float(total)) if total > 0 else 0.0

    with st.container(border=True):
        st.markdown(f"**Task:** `{task_id}`")
        st.caption(
            f"Status: {status} | total={total} success={success_count} "
            f"failed={failure_count} running={running_count} pending={pending_count}"
        )
        st.progress(progress, text=f"{done}/{total} completed")

        items = task_data.get("items")
        if not isinstance(items, list):
            st.warning("Task item payload is invalid.")
            return

        rows: list[dict[str, Any]] = []
        failed_items: list[dict[str, Any]] = []
        for item in items:
            if not isinstance(item, dict):
                continue
            item_status = str(item.get("status", "UNKNOWN"))
            rows.append(
                {
                    "itemId": item.get("itemId"),
                    "fileName": item.get("fileName"),
                    "status": item_status,
                    "retryCount": item.get("retryCount", 0),
                    "errorMessage": item.get("errorMessage"),
                    "key": item.get("key"),
                }
            )
            if item_status == "FAILED":
                failed_items.append(item)

        if failed_items:
            st.markdown("**Failed Items: Retry**")
            retry_all_payload = {"taskId": task_id, "failedCount": len(failed_items)}
            if st.button(
                f"Retry All Failed ({len(failed_items)})",
                key=f"retry_all_{task_id}",
                disabled=_is_action_busy("batch_retry_all", retry_all_payload),
            ):
                _queue_action("batch_retry_all", retry_all_payload)
                st.rerun()

            if _consume_queued_action("batch_retry_all", retry_all_payload):
                try:
                    with st.spinner("Retrying all failed items..."):
                        retried_all = _retry_all_failed_batch_task_items(
                            base_url=base_url,
                            access_token=access_token,
                            task_id=task_id,
                        )
                    if retried_all is not None:
                        _mark_batch_task_active(task_id)
                        st.success("Retry-all submitted.")
                finally:
                    _complete_action("batch_retry_all")
                    st.rerun()

        st.markdown("**Items**")
        item_col_widths = [2.4, 1.0, 0.8, 3.0, 1.2]
        header = st.columns(item_col_widths)
        header[0].markdown("`fileName`")
        header[1].markdown("`status`")
        header[2].markdown("`retry`")
        header[3].markdown("`error`")
        header[4].markdown("`action`")

        for row in rows:
            file_name = str(row.get("fileName", ""))
            item_id = str(row.get("itemId", ""))
            status_text = str(row.get("status", ""))
            retry_count = int(row.get("retryCount", 0) or 0)
            error_text = str(row.get("errorMessage", "") or "")
            cols = st.columns(item_col_widths)
            cols[0].write(file_name)
            cols[1].write(status_text)
            cols[2].write(str(retry_count))
            cols[3].write(error_text if error_text else "-")

            if status_text == "FAILED" and item_id:
                retry_item_payload = {"taskId": task_id, "itemId": item_id}
                if cols[4].button(
                    "Retry",
                    key=f"retry_{task_id}_{item_id}",
                    disabled=_is_action_busy("batch_retry_item", retry_item_payload),
                ):
                    _queue_action("batch_retry_item", retry_item_payload)
                    st.rerun()

                if _consume_queued_action("batch_retry_item", retry_item_payload):
                    try:
                        with st.spinner(f"Retrying {file_name}..."):
                            retried = _retry_batch_task_item(
                                base_url=base_url,
                                access_token=access_token,
                                task_id=task_id,
                                item_id=item_id,
                            )
                        if retried is not None:
                            _mark_batch_task_active(task_id)
                            st.success(f"Retry submitted for {file_name}")
                    finally:
                        _complete_action("batch_retry_item")
                        st.rerun()
            else:
                cols[4].write("-")


def _mark_batch_task_active(task_id: str) -> None:
    active_task_ids_key = "batch_active_task_ids"
    active_ids = st.session_state.get(active_task_ids_key)
    if not isinstance(active_ids, list):
        active_ids = []
    if task_id not in active_ids:
        active_ids.append(task_id)
    st.session_state[active_task_ids_key] = active_ids


def _request_admin_endpoint(
    *,
    base_url: str,
    path: str,
    admin_secret: str,
    params: dict[str, str] | None,
) -> None:
    try:
        response = requests.get(
            f"{base_url}{path}",
            headers={"X-Admin-Secret": admin_secret},
            params=params,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException as exc:
        st.error(f"Request failed: {exc}")
        return

    st.caption(f"HTTP {response.status_code}")
    try:
        payload = response.json()
        st.json(payload)
    except ValueError:
        st.code(response.text[:1000], language="text")


def _fetch_and_decrypt_sts(
    *,
    base_url: str,
    access_token: str,
    encrypt_key_b64: str,
    encrypt_iv_b64: str,
) -> dict[str, str] | None:
    try:
        response = requests.get(
            f"{base_url}/api/v1/auth/sts",
            headers={"X-Access-Token": access_token},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException as exc:
        st.error(f"Failed to call /auth/sts: {exc}")
        return None

    if response.status_code >= 400:
        st.error(f"/auth/sts HTTP {response.status_code}: {response.text[:300]}")
        return None

    try:
        envelope = response.json()
    except ValueError:
        st.error("Invalid /auth/sts response: non-JSON")
        return None

    if not isinstance(envelope, dict) or envelope.get("code") != SUCCESS_CODE:
        st.error(f"/auth/sts business error: {envelope}")
        return None

    encrypted = envelope.get("data")
    if not isinstance(encrypted, str) or not encrypted.strip():
        st.error("Invalid /auth/sts payload: missing encrypted data")
        return None

    plain_text = _aes_cbc_pkcs5_decrypt_base64(
        ciphertext_b64=encrypted.strip(),
        key_b64=encrypt_key_b64,
        iv_b64=encrypt_iv_b64,
    )
    if plain_text is None:
        return None

    try:
        parsed = json.loads(plain_text)
    except ValueError as exc:
        st.error(f"Failed to parse STS JSON: {exc}")
        return None

    for field in ["accessKeyId", "accessKeySecret", "securityToken"]:
        value = parsed.get(field)
        if not isinstance(value, str) or not value:
            st.error(f"Decrypted STS missing field: {field}")
            return None

    return {
        "accessKeyId": parsed["accessKeyId"],
        "accessKeySecret": parsed["accessKeySecret"],
        "securityToken": parsed["securityToken"],
    }


def _load_env_config() -> dict[str, str]:
    cfg: dict[str, str] = {}
    env_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), ".env")

    if os.path.exists(env_path):
        try:
            with open(env_path, "r", encoding="utf-8") as f:
                for raw in f:
                    line = raw.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, value = line.split("=", 1)
                    cfg[key.strip()] = value.strip()
        except OSError:
            pass

    for key in ["ALIYUN_OSS_BUCKET_NAME", "OSS_ENDPOINT", "APP_ENCRYPT_KEY", "APP_ENCRYPT_IV"]:
        value = os.getenv(key)
        if value:
            cfg[key] = value

    return cfg


def _aes_cbc_pkcs5_decrypt_base64(*, ciphertext_b64: str, key_b64: str, iv_b64: str) -> str | None:
    try:
        from Crypto.Cipher import AES
        from Crypto.Util.Padding import unpad
    except ImportError:
        st.error("Missing dependency: pycryptodome. Please install requirements.")
        return None

    try:
        key = base64.b64decode(key_b64)
        iv = base64.b64decode(iv_b64)
        ciphertext = base64.b64decode(ciphertext_b64)
        cipher = AES.new(key, AES.MODE_CBC, iv)
        plain = unpad(cipher.decrypt(ciphertext), AES.block_size)
        return plain.decode("utf-8")
    except Exception as exc:
        st.error(f"Failed to decrypt STS payload: {exc}")
        return None


def _upload_files_to_oss(
    *,
    files: list[Any],
    key_prefix: str,
    bucket_name: str,
    endpoint: str,
    sts: dict[str, str],
) -> list[dict[str, str]]:
    try:
        import oss2
    except ImportError:
        st.error("Missing dependency: oss2. Please install requirements.")
        return []

    endpoint_url = endpoint if endpoint.startswith("http") else f"https://{endpoint}"
    auth = oss2.StsAuth(sts["accessKeyId"], sts["accessKeySecret"], sts["securityToken"])
    bucket = oss2.Bucket(auth, endpoint_url, bucket_name)

    items: list[dict[str, str]] = []
    for file in files:
        content = file.getvalue()
        md5_hex = hashlib.md5(content).hexdigest()
        object_key = f"{key_prefix.rstrip('/')}/{uuid.uuid4().hex}_{file.name}"
        try:
            bucket.put_object(object_key, content, headers={"Content-Type": file.type or "application/octet-stream"})
        except Exception as exc:
            st.error(f"OSS upload failed for {file.name}: {exc}")
            return []

        items.append(
            {
                "key": object_key,
                "fileName": file.name,
                "fileHash": md5_hex,
            }
        )

    return items


def _submit_batch_task(*, base_url: str, access_token: str, items: list[dict[str, str]]) -> dict[str, Any] | None:
    try:
        response = requests.post(
            f"{base_url}/api/v1/image/batch-tasks",
            headers={"X-Access-Token": access_token, "Content-Type": "application/json"},
            json=items,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException as exc:
        st.error(f"Failed to call /image/batch-tasks: {exc}")
        return None

    st.caption(f"HTTP {response.status_code}")

    try:
        envelope = response.json()
    except ValueError:
        st.error("Invalid batch task response: non-JSON")
        st.code(response.text[:1000], language="text")
        return None

    if not isinstance(envelope, dict):
        st.error("Invalid batch task response shape")
        return None

    if envelope.get("code") != SUCCESS_CODE:
        st.error(f"Submit task failed: {envelope}")
        return None

    return envelope


def _get_batch_task_status(*, base_url: str, access_token: str, task_id: str) -> dict[str, Any] | None:
    try:
        response = requests.get(
            f"{base_url}/api/v1/image/batch-tasks/{task_id}",
            headers={"X-Access-Token": access_token},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException as exc:
        st.error(f"Failed to query task status: {exc}")
        return None

    try:
        envelope = response.json()
    except ValueError:
        st.error(f"Invalid task status response for task {task_id}")
        return None

    if not isinstance(envelope, dict):
        st.error(f"Invalid task status response shape for task {task_id}")
        return None
    if envelope.get("code") != SUCCESS_CODE:
        st.error(f"Query task status failed for {task_id}: {envelope}")
        return None
    return envelope


def _retry_batch_task_item(*, base_url: str, access_token: str, task_id: str, item_id: str) -> dict[str, Any] | None:
    try:
        response = requests.post(
            f"{base_url}/api/v1/image/batch-tasks/{task_id}/items/{item_id}/retry",
            headers={"X-Access-Token": access_token},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException as exc:
        st.error(f"Failed to retry item: {exc}")
        return None

    try:
        envelope = response.json()
    except ValueError:
        st.error("Invalid retry response: non-JSON")
        return None

    if not isinstance(envelope, dict):
        st.error("Invalid retry response shape")
        return None
    if envelope.get("code") != SUCCESS_CODE:
        st.error(f"Retry failed: {envelope}")
        return None

    return envelope


def _retry_all_failed_batch_task_items(*, base_url: str, access_token: str, task_id: str) -> dict[str, Any] | None:
    try:
        response = requests.post(
            f"{base_url}/api/v1/image/batch-tasks/{task_id}/retry-failed",
            headers={"X-Access-Token": access_token},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException as exc:
        st.error(f"Failed to retry all failed items: {exc}")
        return None

    try:
        envelope = response.json()
    except ValueError:
        st.error("Invalid retry-all response: non-JSON")
        return None

    if not isinstance(envelope, dict):
        st.error("Invalid retry-all response shape")
        return None
    if envelope.get("code") != SUCCESS_CODE:
        st.error(f"Retry-all failed: {envelope}")
        return None

    return envelope


def _request_json(
    method: str,
    url: str,
    *,
    params: dict[str, Any] | None = None,
    json: dict[str, Any] | None = None,
    files: dict[str, Any] | None = None,
) -> tuple[list[Any], dict[str, str], int] | None:
    try:
        response = requests.request(
            method=method,
            url=url,
            params=params,
            json=json,
            files=files,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.Timeout:
        st.error("Request timed out. Please check backend status and retry.")
        return None
    except requests.exceptions.RequestException as exc:
        st.error(f"Request failed: {exc}")
        return None

    status_code = response.status_code
    headers = {
        "X-Strategy-Requested": response.headers.get("X-Strategy-Requested", ""),
        "X-Strategy-Effective": response.headers.get("X-Strategy-Effective", ""),
        "X-Strategy-Fallback": response.headers.get("X-Strategy-Fallback", ""),
        "X-Strategy-Fallback-Reason": response.headers.get("X-Strategy-Fallback-Reason", ""),
    }

    if status_code >= 400:
        st.error(f"HTTP {status_code}: {response.text[:600]}")
        return None

    try:
        envelope = response.json()
    except ValueError:
        st.error("Backend returned non-JSON response.")
        return None

    if not isinstance(envelope, dict):
        st.error("Unexpected response shape: top-level JSON is not an object.")
        return None

    biz_code = envelope.get("code")
    message = envelope.get("message", "")
    data = envelope.get("data")

    if biz_code != SUCCESS_CODE:
        st.error(f"Business error {biz_code}: {message or 'Unknown error'}")
        return None

    if data is None:
        data = []

    if not isinstance(data, list):
        st.error("Unexpected response shape: data is not a list.")
        return None

    return data, headers, status_code


def _iter_sse_events(response: requests.Response):
    event_name = "message"
    data_lines: list[str] = []
    for raw_line in response.iter_lines(decode_unicode=False):
        if raw_line is None:
            continue
        if isinstance(raw_line, bytes):
            line = raw_line.decode("utf-8", errors="replace").strip()
        else:
            line = str(raw_line).strip()
        if not line:
            if data_lines:
                payload_text = "\n".join(data_lines).strip()
                if payload_text:
                    yield event_name, _parse_sse_payload(payload_text)
            event_name = "message"
            data_lines = []
            continue
        if line.startswith(":"):
            continue
        if line.startswith("event:"):
            event_name = line[6:].strip() or "message"
            continue
        if line.startswith("data:"):
            data_lines.append(line[5:].strip())


def _parse_sse_payload(payload_text: str) -> Any:
    try:
        return json.loads(payload_text)
    except ValueError:
        return {"text": payload_text}


def _render_typewriter_delta(box: Any, base_text: str, delta: str) -> str:
    if not delta:
        box.write(base_text if base_text else "-")
        return base_text

    # Keep UI responsive for long content: animate short chunks, fast-append long chunks.
    should_fast_append = len(base_text) > 2000
    if should_fast_append:
        updated = base_text + delta
        box.write(updated if updated else "-")
        return updated

    updated = base_text
    for ch in delta:
        updated += ch
        box.write(f"{updated}▌")
        time.sleep(0.012)
    box.write(updated if updated else "-")
    return updated


def _render_response_meta(status_code: int, headers: dict[str, str]) -> None:
    st.caption(f"HTTP {status_code}")
    if any(headers.values()):
        st.markdown("#### Strategy Debug Headers")
        st.json({k: v for k, v in headers.items() if v})


def _render_search_results(results: list[Any], *, layout_key: str, highlight_term: str = "") -> None:
    st.markdown("### Results")
    if not results:
        st.info("No results found.")
        return
    _inject_result_text_styles()

    pref_key = f"result_layout_pref_{layout_key}"
    if pref_key not in st.session_state:
        st.session_state[pref_key] = LAYOUT_OPTIONS[0]

    current_pref = st.session_state[pref_key]
    if current_pref not in LAYOUT_OPTIONS:
        current_pref = LAYOUT_OPTIONS[0]

    layout_mode = st.radio(
        "Result Layout",
        options=LAYOUT_OPTIONS,
        index=LAYOUT_OPTIONS.index(current_pref),
        horizontal=True,
        key=f"result_layout_mode_{layout_key}",
    )
    st.session_state[pref_key] = layout_mode

    num_cols = 2 if layout_mode.startswith("Large") else 3

    normalized_results = [item for item in results if isinstance(item, dict)]
    if not normalized_results:
        st.error("Unexpected response shape: result items are not objects.")
        return

    cols = st.columns(num_cols)
    # Masonry-like distribution: keep adding cards down each column to reduce row-gap blank space.
    for idx, item in enumerate(normalized_results):
        with cols[idx % num_cols]:
            image_url = _safe_str(item.get("url"))
            filename = _safe_str(item.get("filename")) or "Unnamed"
            doc_id = _safe_str(item.get("id")) or "-"
            score = "-" if item.get("score") is None else str(item.get("score"))
            tags = item.get("tags")
            tags_text = ", ".join(str(tag) for tag in tags[:8]) if isinstance(tags, list) and tags else "-"
            highlight = _clip_text(_safe_str(item.get("highlight")), HIGHLIGHT_PREVIEW_MAX_CHARS)
            ocr_text = _clip_text(_safe_str(item.get("ocrText")), OCR_PREVIEW_MAX_CHARS)
            relations_text = _clip_text(
                _format_relations_preview(item.get("relations"), max_items=SPO_PREVIEW_MAX_ITEMS),
                SPO_PREVIEW_MAX_CHARS,
            )
            vector_status = _safe_str(item.get("vectorHitStatus")) or "-"
            explain = item.get("explain") if isinstance(item.get("explain"), dict) else {}
            hit_sources = _normalize_hit_sources(explain.get("hitSources"))
            hit_sources_html = _render_hit_source_badges(hit_sources)
            matched_by_text = _format_matched_by(explain.get("matchedBy"))
            strategy_effective = _safe_str(explain.get("strategyEffective")) or "-"
            tags_html = _highlight_text_html(tags_text, highlight_term)
            ocr_html = _highlight_text_html(ocr_text, highlight_term) if ocr_text else ""
            spo_html = _highlight_text_html(relations_text, highlight_term) if relations_text else ""

            card_html = [
                "<div class='result-card'>",
                _render_large_preview(image_url, filename, highlight_term=highlight_term),
                "<div class='result-meta'>",
            ]
            card_html.append(_meta_row("id", doc_id))
            card_html.append(_meta_row("score", score))
            card_html.append(_meta_row_html("tags", tags_html))
            if relations_text:
                card_html.append(_meta_row_single_line_html("spo", spo_html, relations_text))
            card_html.append(_meta_row("vector", vector_status))
            if hit_sources_html:
                card_html.append(_meta_row_html("hitSources", hit_sources_html))
            if strategy_effective != "-":
                card_html.append(_meta_row("strategy", strategy_effective))
            if matched_by_text:
                card_html.append(_meta_row_single_line("matchedBy", matched_by_text))
            if highlight:
                card_html.append(_meta_row("highlight", highlight))
            if ocr_text:
                card_html.append(_meta_row_html("ocrText", ocr_html))
            card_html.append("</div></div>")
            st.markdown("".join(card_html), unsafe_allow_html=True)


def _format_relations_preview(relations: Any, *, max_items: int) -> str:
    rows = _normalize_relations(relations)
    if not rows:
        return ""

    triples = [_format_relation_line(row, delimiter="-") for row in rows[:max_items]]
    return "; ".join(triples)


def _highlight_text_html(text: str, keyword: str) -> str:
    safe_text = html.escape(_safe_str(text))
    key = _safe_str(keyword)
    if not safe_text or not key:
        return safe_text
    pattern = re.compile(re.escape(key), re.IGNORECASE)
    return pattern.sub(lambda m: f"<mark class='hit-mark'>{m.group(0)}</mark>", safe_text)


def _normalize_relations(relations: Any) -> list[dict[str, str]]:
    if not isinstance(relations, list):
        return []

    rows: list[dict[str, str]] = []
    for triple in relations:
        if not isinstance(triple, dict):
            continue
        s = _safe_str(triple.get("s"))
        p = _safe_str(triple.get("p"))
        o = _safe_str(triple.get("o"))
        if not (s or p or o):
            continue
        rows.append({"s": s, "p": p, "o": o})
    return rows


def _format_relation_line(row: dict[str, str], *, delimiter: str) -> str:
    parts = [part for part in [row.get("s", ""), row.get("p", ""), row.get("o", "")] if part]
    return delimiter.join(parts)


def _safe_str(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def _normalize_hit_sources(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    normalized: list[str] = []
    for item in value:
        source = _safe_str(item).upper()
        if source in {"VECTOR", "OCR", "TAG", "GRAPH"} and source not in normalized:
            normalized.append(source)
    return normalized


def _render_hit_source_badges(hit_sources: list[str]) -> str:
    if not hit_sources:
        return ""
    return "".join(
        f"<span class='hit-source-badge hit-source-{source.lower()}'>{html.escape(source)}</span>"
        for source in hit_sources
    )


def _format_matched_by(value: Any) -> str:
    if not isinstance(value, dict):
        return ""
    pairs: list[str] = []
    for key in ["vector", "filename", "ocr", "tag", "graph"]:
        raw = value.get(key)
        if isinstance(raw, bool):
            pairs.append(f"{key}={'Y' if raw else 'N'}")
    return ", ".join(pairs)


def _validate_uploaded_image_size(uploaded: Any, label: str) -> bool:
    if uploaded is None:
        return False
    size = int(getattr(uploaded, "size", 0) or 0)
    if size <= 0:
        return True
    if size > MAX_IMAGE_UPLOAD_BYTES:
        st.warning(f"{label} is too large ({size / 1024 / 1024:.2f}MB). Please upload an image within 10MB.")
        return False
    return True


def _uploaded_file_fingerprint(uploaded: Any) -> dict[str, Any]:
    if uploaded is None:
        return {}
    return {
        "name": str(getattr(uploaded, "name", "")),
        "size": int(getattr(uploaded, "size", 0) or 0),
        "type": str(getattr(uploaded, "type", "")),
    }


def _idempotent_signature(payload: Any) -> str:
    normalized_payload = json.dumps(payload, ensure_ascii=False, sort_keys=True, default=str)
    return hashlib.sha256(normalized_payload.encode("utf-8")).hexdigest()


def _get_action_registry() -> dict[str, Any]:
    key = "__action_registry"
    registry = st.session_state.get(key)
    if not isinstance(registry, dict):
        registry = {}
        st.session_state[key] = registry
    return registry


def _is_action_busy(action_key: str, payload: Any) -> bool:
    registry = _get_action_registry()
    record = registry.get(action_key)
    if not isinstance(record, dict):
        return False
    status = str(record.get("status", "idle"))
    now = time.time()
    queued_at = float(record.get("queuedAt", 0) or 0)
    running_at = float(record.get("runningAt", 0) or 0)
    if status == "queued" and queued_at > 0 and now - queued_at > ACTION_STALE_TIMEOUT_SECONDS:
        registry.pop(action_key, None)
        return False
    if status == "running" and running_at > 0 and now - running_at > ACTION_STALE_TIMEOUT_SECONDS:
        registry.pop(action_key, None)
        return False
    signature = str(record.get("signature", ""))
    return status in {"queued", "running"} and signature == _idempotent_signature(payload)


def _queue_action(action_key: str, payload: Any) -> None:
    _get_action_registry()[action_key] = {
        "status": "queued",
        "signature": _idempotent_signature(payload),
        "queuedAt": time.time(),
    }


def _consume_queued_action(action_key: str, payload: Any) -> bool:
    record = _get_action_registry().get(action_key)
    if not isinstance(record, dict):
        return False
    if str(record.get("status", "")) != "queued":
        return False
    if str(record.get("signature", "")) != _idempotent_signature(payload):
        return False
    record["status"] = "running"
    record["runningAt"] = time.time()
    return True


def _complete_action(action_key: str) -> None:
    _get_action_registry().pop(action_key, None)


def _normalize_base_url(base_url: str) -> str:
    return (base_url or "http://localhost:8080").strip().rstrip("/")


def _pretty_json(payload: dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2)


def _get_result_state(page: str) -> dict[str, Any]:
    key = RESULT_STATE_KEYS[page]
    if key not in st.session_state:
        st.session_state[key] = {
            "has_searched": False,
            "results": [],
            "headers": {},
            "status_code": 200,
            "payload_text": "",
        }
    return st.session_state[key]


def _render_large_preview(image_url: str, filename: str, highlight_term: str = "") -> str:
    safe_filename = _highlight_text_html(filename, highlight_term) if _safe_str(highlight_term) else html.escape(filename)
    if image_url:
        safe_image_url = html.escape(image_url, quote=True)
        return (
            "<div class='result-image-wrap'>"
            f"<img src=\"{safe_image_url}\" class='result-image' />"
            f"<div class='result-filename'>{safe_filename}</div>"
            "</div>"
        )
    return (
        "<div class='result-image-wrap result-image-empty'>"
        "<div class='result-no-image'>No image URL</div>"
        f"<div class='result-filename'>{safe_filename}</div>"
        "</div>"
    )


def _inject_result_text_styles() -> None:
    st.markdown(
        """
        <style>
        .result-card {
          margin-top: 4px;
          border: 1px solid rgba(120, 130, 150, 0.32);
          border-radius: 12px;
          background: rgba(255, 255, 255, 0.03);
          overflow: hidden;
        }
        .result-image-wrap {
          position: relative;
          width: 100%;
          background: rgba(13, 18, 28, 0.72);
        }
        .result-image {
          width: 100%;
          height: auto;
          display: block;
        }
        .result-image-empty {
          min-height: 160px;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .result-no-image {
          color: #8b9bb0;
          font-size: 12px;
        }
        .result-filename {
          position: absolute;
          right: 8px;
          bottom: 8px;
          max-width: calc(100% - 16px);
          padding: 2px 6px;
          border-radius: 6px;
          font-size: 11px;
          line-height: 1.2;
          color: #f4f8ff;
          background: rgba(12, 16, 24, 0.78);
          backdrop-filter: blur(2px);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .result-meta {
          padding: 6px 8px 7px;
          border-top: 1px solid rgba(120, 130, 150, 0.2);
          background: rgba(4, 8, 14, 0.3);
        }
        .result-row {
          margin: 1px 0;
          line-height: 1.18;
          font-size: 11px;
          display: flex;
          align-items: flex-start;
          gap: 7px;
        }
        .result-key {
          min-width: 56px;
          color: #8ea0b6;
          font-weight: 700;
          letter-spacing: 0.01em;
          text-transform: none;
        }
        .result-val {
          color: #e7eef7;
          font-weight: 400;
          word-break: break-word;
          overflow-wrap: anywhere;
        }
        .result-val-one-line {
          color: #e7eef7;
          font-weight: 400;
          display: block;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .hit-mark {
          background: rgba(255, 216, 107, 0.92);
          color: #2b1f00;
          border-radius: 3px;
          padding: 0 4px;
        }
        .hit-source-badge {
          display: inline-block;
          margin-right: 4px;
          margin-bottom: 3px;
          padding: 1px 6px;
          border-radius: 999px;
          font-size: 10px;
          font-weight: 700;
          letter-spacing: 0.02em;
          color: #0d1522;
          background: #9fb4cf;
        }
        .hit-source-vector { background: #7dd3fc; }
        .hit-source-ocr { background: #86efac; }
        .hit-source-tag { background: #fcd34d; }
        .hit-source-graph { background: #f9a8d4; }
        </style>
        """,
        unsafe_allow_html=True,
    )


def _meta_row(key: str, value: str) -> str:
    return (
        "<div class='result-row'>"
        f"<span class='result-key'>{html.escape(key)}:</span>"
        f"<span class='result-val'>{html.escape(value)}</span>"
        "</div>"
    )


def _meta_row_single_line(key: str, value: str) -> str:
    safe_value = html.escape(value)
    return (
        "<div class='result-row'>"
        f"<span class='result-key'>{html.escape(key)}:</span>"
        f"<span class='result-val-one-line' title='{safe_value}'>{safe_value}</span>"
        "</div>"
    )


def _meta_row_html(key: str, value_html: str) -> str:
    return (
        "<div class='result-row'>"
        f"<span class='result-key'>{html.escape(key)}:</span>"
        f"<span class='result-val'>{value_html}</span>"
        "</div>"
    )


def _meta_row_single_line_html(key: str, value_html: str, raw_value: str) -> str:
    return (
        "<div class='result-row'>"
        f"<span class='result-key'>{html.escape(key)}:</span>"
        f"<span class='result-val-one-line' title='{html.escape(raw_value)}'>{value_html}</span>"
        "</div>"
    )


def _clip_text(text: str, max_chars: int) -> str:
    if not text:
        return ""
    text = text.strip()
    return text if len(text) <= max_chars else text[:max_chars] + "..."
