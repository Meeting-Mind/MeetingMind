from urllib.parse import urlparse


OPENAI_API_HOST = "api.openai.com"


def local_provider_base_url_error(value: str | None, *, label: str = "local provider base url") -> str | None:
    parsed = urlparse((value or "").strip())
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        return f"{label} must be an absolute http(s) URL"
    if (parsed.hostname or "").casefold() == OPENAI_API_HOST:
        return f"{label} must not point to {OPENAI_API_HOST}"
    if parsed.username or parsed.password:
        return f"{label} must not include userinfo credentials"
    if parsed.query or parsed.fragment:
        return f"{label} must not include query or fragment"
    return None


def local_provider_base_url_is_compatible(value: str | None) -> bool:
    return local_provider_base_url_error(value) is None
