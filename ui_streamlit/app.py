import streamlit as st

from pages import render_hot_words_page
from pages import render_auth_admin_page
from pages import render_image_batch_process_page
from pages import render_image_search_page
from pages import render_similar_search_page
from pages import render_text_search_page

st.set_page_config(
    page_title="Smart Vision UI Demo",
    page_icon="🖼️",
    layout="wide",
)


def init_state() -> None:
    if "base_url" not in st.session_state:
        st.session_state.base_url = "http://localhost:8080"
    if "theme_mode" not in st.session_state:
        st.session_state.theme_mode = "Light"
    if "show_debug_panel" not in st.session_state:
        st.session_state.show_debug_panel = True
    if "request_timeout_seconds" not in st.session_state:
        st.session_state.request_timeout_seconds = 20


def render_sidebar() -> str:
    with st.sidebar:
        st.title("Smart Vision")
        st.caption("Streamlit validation UI")
        st.session_state.base_url = st.text_input(
            "Backend Base URL",
            value=st.session_state.base_url,
            help="Example: http://localhost:8080",
        ).strip()
        st.session_state.theme_mode = st.selectbox(
            "Theme",
            ["Light", "Dark", "System"],
            index=["Light", "Dark", "System"].index(st.session_state.theme_mode),
        )
        st.divider()
        st.session_state.show_debug_panel = st.checkbox(
            "Show Debug Panel",
            value=st.session_state.show_debug_panel,
            help="Display request logs and latest request/response details on each page.",
        )
        st.session_state.request_timeout_seconds = int(
            st.number_input(
                "Request Timeout (s)",
                min_value=5,
                max_value=120,
                value=int(st.session_state.request_timeout_seconds),
                step=1,
            )
        )
        page = st.radio(
            "Pages",
            [
                "Text Search",
                "Search By Image",
                "Similar Search",
                "Hot Words",
                "Batch Process",
                "Auth Admin",
            ],
        )
    return page


def main() -> None:
    init_state()
    current_page = render_sidebar()

    st.title("Smart Vision Frontend Validation")
    # st.caption("UI-TASK-05: pagination, resilience, and debugability")
    st.divider()

    if current_page == "Text Search":
        render_text_search_page(st.session_state.base_url)
        return
    if current_page == "Search By Image":
        render_image_search_page(st.session_state.base_url)
        return
    if current_page == "Similar Search":
        render_similar_search_page(st.session_state.base_url)
        return
    if current_page == "Batch Process":
        render_image_batch_process_page(st.session_state.base_url)
        return
    if current_page == "Auth Admin":
        render_auth_admin_page(st.session_state.base_url)
        return
    render_hot_words_page(st.session_state.base_url)


if __name__ == "__main__":
    main()
