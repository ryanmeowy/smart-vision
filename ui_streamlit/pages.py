from __future__ import annotations

import base64
import hashlib
import json
import uuid
from typing import Any

import requests
import streamlit as st

REQUEST_TIMEOUT_SECONDS = 20
SUCCESS_CODE = 200


def render_text_search_page(base_url: str) -> None:
    st.subheader("Text Search")
    st.caption("Endpoint: POST /api/v1/vision/search")

    with st.form("text_search_form"):
        col1, col2, col3 = st.columns([3, 1, 1])
        keyword = col1.text_input("Keyword", placeholder="e.g. 猫咪在沙发上")
        search_type = col2.selectbox(
            "Strategy",
            options=["0", "1", "2", "3"],
            help="0: hybrid, 1: vector, 2: text, 3: image-to-image",
        )
        limit = col3.number_input("Limit", min_value=1, max_value=100, value=20)
        top_k = st.number_input("TopK", min_value=1, max_value=200, value=30)
        enable_ocr = st.checkbox("Enable OCR", value=True)
        submitted = st.form_submit_button("Run Search", type="primary")

    if submitted:
        if not keyword.strip():
            st.warning("Keyword is required.")
            return

        payload = {
            "keyword": keyword.strip(),
            "searchType": str(search_type),
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

        _render_response_meta(status_code, headers)
        st.code(_pretty_json(payload), language="json")
        _render_search_results(data)


def render_image_search_page(base_url: str) -> None:
    st.subheader("Search By Image")
    st.caption("Endpoint: POST /api/v1/vision/search-by-image")

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

        _render_response_meta(status_code, headers)
        st.code(
            f"POST {_normalize_base_url(base_url)}/api/v1/vision/search-by-image?limit={int(limit)}",
            language="bash",
        )
        _render_search_results(data)


def render_similar_search_page(base_url: str) -> None:
    st.subheader("Similar Search")
    st.caption("Endpoint: GET /api/v1/vision/similar")

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

        _render_response_meta(status_code, headers)
        st.code(
            f"GET {_normalize_base_url(base_url)}/api/v1/vision/similar?id={image_id.strip()}",
            language="bash",
        )
        _render_search_results(data)


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

    bucket_name = st.text_input("OSS Bucket", value="", placeholder="Input manually")
    oss_endpoint = st.text_input("OSS Endpoint", value="", placeholder="Input manually")
    encrypt_key = st.text_input(
        "APP_ENCRYPT_KEY (base64)",
        value="",
        placeholder="Input manually",
        type="password",
    )
    encrypt_iv = st.text_input(
        "APP_ENCRYPT_IV (base64)",
        value="",
        placeholder="Input manually",
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


def _render_search_results(results: list[Any]) -> None:
    st.markdown("### Results")
    if not results:
        st.info("No results found.")
        return

    normalized_results = [item for item in results if isinstance(item, dict)]
    if not normalized_results:
        st.error("Unexpected response shape: result items are not objects.")
        return

    for row_start in range(0, len(normalized_results), 3):
        row = normalized_results[row_start : row_start + 3]
        cols = st.columns(3)
        for idx, item in enumerate(row):
            with cols[idx]:
                with st.container(border=True):
                    image_url = _safe_str(item.get("url"))
                    if image_url:
                        st.image(image_url, use_container_width=True)
                    else:
                        st.caption("No image URL")

                    st.markdown(f"**{_safe_str(item.get('filename')) or 'Unnamed'}**")
                    st.caption(f"id: {_safe_str(item.get('id')) or '-'}")
                    score = item.get("score")
                    if score is not None:
                        st.text(f"score: {score}")

                    tags = item.get("tags")
                    if isinstance(tags, list) and tags:
                        st.text("tags: " + ", ".join(str(tag) for tag in tags[:8]))

                    highlight = _safe_str(item.get("highlight"))
                    if highlight:
                        st.caption("highlight")
                        st.write(highlight)

                    ocr_text = _safe_str(item.get("ocrText"))
                    if ocr_text:
                        st.caption("ocrText")
                        st.write(ocr_text[:160] + ("..." if len(ocr_text) > 160 else ""))


def _safe_str(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def _normalize_base_url(base_url: str) -> str:
    return (base_url or "http://localhost:8080").strip().rstrip("/")


def _pretty_json(payload: dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2)
