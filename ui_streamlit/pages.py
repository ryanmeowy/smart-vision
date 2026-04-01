from __future__ import annotations

import base64
import html
import hashlib
import json
import os
import uuid
from typing import Any

import requests
import streamlit as st

REQUEST_TIMEOUT_SECONDS = 20
SUCCESS_CODE = 200
RESULT_STATE_KEYS = {
    "text": "result_state_text",
    "image": "result_state_image",
    "similar": "result_state_similar",
}
LAYOUT_OPTIONS = ["Large (2 per row)", "Compact (3 per row)"]
OCR_PREVIEW_MAX_CHARS = 100
HIGHLIGHT_PREVIEW_MAX_CHARS = 80


def render_text_search_page(base_url: str) -> None:
    st.subheader("Text Search")
    st.caption("Endpoint: POST /api/v1/vision/search")

    state = _get_result_state("text")

    with st.form("text_search_form"):
        row1_col1, row1_col2 = st.columns([5, 2])
        keyword = row1_col1.text_input("Keyword", placeholder="e.g. 猫咪在沙发上")
        strategy = row1_col2.selectbox(
            "Strategy",
            options=["0", "1", "2", "3"],
            help="0: hybrid, 1: vector, 2: text, 3: image-to-image",
        )
        row2_col1, row2_col2 = st.columns([2, 2])
        top_k = row2_col1.number_input("TopK", min_value=1, max_value=200, value=30)
        limit = row2_col2.number_input("Limit", min_value=1, max_value=100, value=20)
        enable_ocr = st.checkbox("Enable OCR", value=True)
        submitted = st.form_submit_button("Run Search", type="primary")

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

        with st.spinner("Searching..."):
            data, headers, status_code = _request_json(
                method="POST",
                url=f"{_normalize_base_url(base_url)}/api/v1/vision/search",
                json=payload,
            )

        state["results"] = data
        state["headers"] = headers
        state["status_code"] = status_code
        state["payload_text"] = _pretty_json(payload)
        state["has_searched"] = True

    if state["has_searched"]:
        _render_response_meta(state["status_code"], state["headers"])
        if state["payload_text"]:
            st.code(state["payload_text"], language="json")
        _render_search_results(state["results"], layout_key="text")


def render_image_search_page(base_url: str) -> None:
    st.subheader("Search By Image")
    st.caption("Endpoint: POST /api/v1/vision/search-by-image")

    state = _get_result_state("image")

    with st.form("image_search_form"):
        uploaded = st.file_uploader(
            "Upload image",
            type=["png", "jpg", "jpeg", "webp"],
            accept_multiple_files=False,
        )
        limit = st.number_input("Limit", min_value=1, max_value=100, value=20)
        submitted = st.form_submit_button("Run Image Search", type="primary")

    if submitted:
        if uploaded is None:
            st.warning("Please upload an image file first.")
            return

        with st.spinner("Searching by image..."):
            data, headers, status_code = _request_json(
                method="POST",
                url=f"{_normalize_base_url(base_url)}/api/v1/vision/search-by-image",
                params={"limit": int(limit)},
                files={"file": (uploaded.name, uploaded.getvalue(), uploaded.type or "application/octet-stream")},
            )

        state["results"] = data
        state["headers"] = headers
        state["status_code"] = status_code
        state["payload_text"] = (
            f"POST {_normalize_base_url(base_url)}/api/v1/vision/search-by-image?limit={int(limit)}"
        )
        state["has_searched"] = True

    if state["has_searched"]:
        _render_response_meta(state["status_code"], state["headers"])
        if state["payload_text"]:
            st.code(state["payload_text"], language="bash")
        _render_search_results(state["results"], layout_key="image")


def render_similar_search_page(base_url: str) -> None:
    st.subheader("Similar Search")
    st.caption("Endpoint: GET /api/v1/vision/similar")

    state = _get_result_state("similar")

    with st.form("similar_search_form"):
        image_id = st.text_input("Image ID", placeholder="e.g. 1234567890")
        submitted = st.form_submit_button("Run Similar Search", type="primary")

    if submitted:
        if not image_id.strip():
            st.warning("Image ID is required.")
            return

        with st.spinner("Searching similar images..."):
            data, headers, status_code = _request_json(
                method="GET",
                url=f"{_normalize_base_url(base_url)}/api/v1/vision/similar",
                params={"id": image_id.strip()},
            )

        state["results"] = data
        state["headers"] = headers
        state["status_code"] = status_code
        state["payload_text"] = f"GET {_normalize_base_url(base_url)}/api/v1/vision/similar?id={image_id.strip()}"
        state["has_searched"] = True

    if state["has_searched"]:
        _render_response_meta(state["status_code"], state["headers"])
        if state["payload_text"]:
            st.code(state["payload_text"], language="bash")
        _render_search_results(state["results"], layout_key="similar")


def render_hot_words_page(base_url: str) -> None:
    st.subheader("Hot Words")
    st.caption("Endpoint: GET /api/v1/vision/hot-words")

    if st.button("Fetch Hot Words", type="primary"):
        with st.spinner("Loading hot words..."):
            data, _, status_code = _request_json(
                method="GET",
                url=f"{_normalize_base_url(base_url)}/api/v1/vision/hot-words",
            )

        st.caption(f"HTTP {status_code}")
        words = data if isinstance(data, list) else []
        if not words:
            st.info("No hot words currently.")
            return

        st.markdown("### Hot Words")
        st.write(words)


def render_auth_admin_page(base_url: str) -> None:
    st.subheader("Auth Admin")
    st.caption("Endpoints: GET /api/v1/auth/refresh-token, GET /api/v1/auth/clean-token")
    st.warning("Admin operation: keep `X-Admin-Secret` private and do not expose in shared demos.")

    admin_secret = st.text_input("X-Admin-Secret", type="password")
    refresh_code = st.text_input("Refresh Code (optional)")

    col1, col2 = st.columns(2)
    if col1.button("Refresh Token", type="primary"):
        if not admin_secret.strip():
            st.warning("X-Admin-Secret is required.")
        else:
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
            _request_admin_endpoint(
                base_url=_normalize_base_url(base_url),
                path="/api/v1/auth/clean-token",
                admin_secret=admin_secret.strip(),
                params=None,
            )


def render_image_batch_process_page(base_url: str) -> None:
    st.subheader("Batch Process")
    st.caption("Endpoint: POST /api/v1/image/batch-process")
    st.info(
        "Integrated flow: fetch STS -> upload to OSS -> call batch-process. "
        "Images do not go through backend file upload in this flow."
    )

    token = st.text_input("X-Access-Token", type="password", help="Required header for protected APIs")
    key_prefix = st.text_input("OSS Key Prefix", value="", placeholder="Input manually, e.g. images/ui-upload/")
    files = st.file_uploader(
        "Select local files for integrated upload",
        type=["png", "jpg", "jpeg", "webp"],
        accept_multiple_files=True,
    )

    env_cfg = _load_env_config()
    bucket_name = st.text_input("OSS Bucket", value=env_cfg.get("ALIYUN_OSS_BUCKET_NAME", ""))
    oss_endpoint = st.text_input("OSS Endpoint", value=env_cfg.get("OSS_ENDPOINT", ""))
    encrypt_key = st.text_input(
        "APP_ENCRYPT_KEY (base64)",
        value=env_cfg.get("APP_ENCRYPT_KEY", ""),
        type="password",
    )
    encrypt_iv = st.text_input(
        "APP_ENCRYPT_IV (base64)",
        value=env_cfg.get("APP_ENCRYPT_IV", ""),
        type="password",
    )

    if st.button("Upload To OSS And Process", type="primary"):
        if not token.strip():
            st.warning("X-Access-Token is required.")
            return
        if not files:
            st.warning("Please select at least one file.")
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

        with st.spinner("Step 3/3: Calling batch-process..."):
            envelope = _call_batch_process(
                base_url=_normalize_base_url(base_url),
                access_token=token.strip(),
                items=items,
            )
        if envelope is None:
            return

        st.markdown("### Batch Result")
        st.json(envelope)


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


def _call_batch_process(*, base_url: str, access_token: str, items: list[dict[str, str]]) -> dict[str, Any] | None:
    try:
        response = requests.post(
            f"{base_url}/api/v1/image/batch-process",
            headers={"X-Access-Token": access_token, "Content-Type": "application/json"},
            json=items,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except requests.exceptions.RequestException as exc:
        st.error(f"Failed to call /image/batch-process: {exc}")
        return None

    st.caption(f"HTTP {response.status_code}")

    try:
        envelope = response.json()
    except ValueError:
        st.error("Invalid batch-process response: non-JSON")
        st.code(response.text[:1000], language="text")
        return None

    if not isinstance(envelope, dict):
        st.error("Invalid batch-process response shape")
        return None

    return envelope


def _request_json(
    method: str,
    url: str,
    *,
    params: dict[str, Any] | None = None,
    json: dict[str, Any] | None = None,
    files: dict[str, Any] | None = None,
) -> tuple[list[Any], dict[str, str], int]:
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
        st.stop()
    except requests.exceptions.RequestException as exc:
        st.error(f"Request failed: {exc}")
        st.stop()

    status_code = response.status_code
    headers = {
        "X-Strategy-Requested": response.headers.get("X-Strategy-Requested", ""),
        "X-Strategy-Effective": response.headers.get("X-Strategy-Effective", ""),
        "X-Strategy-Fallback": response.headers.get("X-Strategy-Fallback", ""),
        "X-Strategy-Fallback-Reason": response.headers.get("X-Strategy-Fallback-Reason", ""),
    }

    if status_code >= 400:
        st.error(f"HTTP {status_code}: {response.text[:600]}")
        st.stop()

    try:
        envelope = response.json()
    except ValueError:
        st.error("Backend returned non-JSON response.")
        st.stop()

    if not isinstance(envelope, dict):
        st.error("Unexpected response shape: top-level JSON is not an object.")
        st.stop()

    biz_code = envelope.get("code")
    message = envelope.get("message", "")
    data = envelope.get("data")

    if biz_code != SUCCESS_CODE:
        st.error(f"Business error {biz_code}: {message or 'Unknown error'}")
        st.stop()

    if data is None:
        data = []

    if not isinstance(data, list):
        st.error("Unexpected response shape: data is not a list.")
        st.stop()

    return data, headers, status_code


def _render_response_meta(status_code: int, headers: dict[str, str]) -> None:
    st.caption(f"HTTP {status_code}")
    if any(headers.values()):
        st.markdown("#### Strategy Debug Headers")
        st.json({k: v for k, v in headers.items() if v})


def _render_search_results(results: list[Any], *, layout_key: str) -> None:
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

            card_html = ["<div class='result-card'>", _render_large_preview(image_url, filename), "<div class='result-meta'>"]
            card_html.append(_meta_row("id", doc_id))
            card_html.append(_meta_row("score", score))
            card_html.append(_meta_row("tags", tags_text))
            if highlight:
                card_html.append(_meta_row("highlight", highlight))
            if ocr_text:
                card_html.append(_meta_row("ocrText", ocr_text))
            card_html.append("</div></div>")
            st.markdown("".join(card_html), unsafe_allow_html=True)


def _safe_str(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


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


def _render_large_preview(image_url: str, filename: str) -> str:
    safe_filename = html.escape(filename)
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


def _clip_text(text: str, max_chars: int) -> str:
    if not text:
        return ""
    text = text.strip()
    return text if len(text) <= max_chars else text[:max_chars] + "..."
