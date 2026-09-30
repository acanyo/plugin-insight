"""Independent probe configuration; no Halo administrator credentials required."""
import os
from dataclasses import dataclass
from urllib.parse import urlsplit


def checked_url(value, allow_http=False):
    parsed = urlsplit(value)
    if parsed.scheme not in (("https", "http") if allow_http else ("https",)):
        raise ValueError("URL must use HTTPS (or explicitly enable ALLOW_HTTP)")
    if not parsed.hostname or parsed.username or parsed.password or parsed.fragment:
        raise ValueError("URL must contain a hostname, without userinfo or fragment")
    return value


@dataclass(frozen=True)
class Config:
    url: str
    interval: int = 30
    timeout: int = 5
    failures: int = 3
    recoveries: int = 2
    expected_text: str = ""
    webhook: str = ""
    webhook_secret: str = ""
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    smtp_from: str = ""
    smtp_to: tuple = ()
    database: str = "/data/monitor.sqlite"

    @classmethod
    def from_env(cls):
        allow_http = os.getenv("ALLOW_HTTP", "false").lower() == "true"
        webhook = os.getenv("WEBHOOK_URL", "")
        if webhook:
            checked_url(webhook, allow_http)
        config = cls(
            url=checked_url(os.environ["MONITOR_URL"], allow_http),
            interval=int(os.getenv("INTERVAL_SECONDS", "30")),
            timeout=int(os.getenv("TIMEOUT_SECONDS", "5")),
            failures=int(os.getenv("FAILURE_THRESHOLD", "3")),
            recoveries=int(os.getenv("RECOVERY_THRESHOLD", "2")),
            expected_text=os.getenv("EXPECTED_TEXT", ""),
            webhook=webhook, webhook_secret=os.getenv("WEBHOOK_SECRET", ""),
            smtp_host=os.getenv("SMTP_HOST", ""), smtp_port=int(os.getenv("SMTP_PORT", "587")),
            smtp_user=os.getenv("SMTP_USERNAME", ""), smtp_password=os.getenv("SMTP_PASSWORD", ""),
            smtp_from=os.getenv("SMTP_FROM", ""),
            smtp_to=tuple(x.strip() for x in os.getenv("SMTP_TO", "").split(",") if x.strip()),
            database=os.getenv("DATABASE_PATH", "/data/monitor.sqlite"),
        )
        if not (5 <= config.interval <= 3600 and 1 <= config.timeout <= 30
                and 1 <= config.failures <= 20 and 1 <= config.recoveries <= 20):
            raise ValueError("Invalid probe intervals or thresholds")
        if not config.webhook and not (config.smtp_host and config.smtp_from and config.smtp_to):
            raise ValueError("Configure an independent Webhook or complete SMTP channel")
        return config

