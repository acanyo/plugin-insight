"""Network calls with TLS verification, no redirects and bounded response reads."""
import hashlib
import hmac
import json
import smtplib
import ssl
import urllib.error
import urllib.request
from email.message import EmailMessage


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def opener():
    return urllib.request.build_opener(NoRedirect())


def probe(config):
    request = urllib.request.Request(config.url, headers={"User-Agent": "Halo-Insight-External/1.0"})
    try:
        with opener().open(request, timeout=config.timeout) as response:
            body = response.read(65536)
            healthy = 200 <= response.status < 300
            if config.expected_text:
                healthy = healthy and config.expected_text.encode("utf-8") in body
            return healthy, "HTTP " + str(response.status) + ("" if healthy else " 或页面内容不匹配")
    except urllib.error.HTTPError as error:
        return False, "HTTP " + str(error.code)
    except Exception as error:
        # Never print URLs, query tokens or raw exception text.
        return False, type(error).__name__


def send(config, channel, event):
    body = json.dumps(event, ensure_ascii=False).encode("utf-8")
    if channel == "webhook":
        headers = {"Content-Type": "application/json", "X-Insight-Event-Id": event["id"]}
        if config.webhook_secret:
            signature = hmac.new(config.webhook_secret.encode(), body, hashlib.sha256).hexdigest()
            headers["X-Insight-Signature"] = "sha256=" + signature
        request = urllib.request.Request(config.webhook, data=body, headers=headers, method="POST")
        with opener().open(request, timeout=config.timeout) as response:
            if not 200 <= response.status < 300:
                raise RuntimeError("Webhook delivery failed")
    elif channel == "email":
        mail = EmailMessage()
        mail["From"] = config.smtp_from
        mail["To"] = ", ".join(config.smtp_to)
        mail["Subject"] = "[Halo Insight] " + event["message"]
        mail["Message-ID"] = "<" + event["id"] + "@insight-monitor>"
        mail.set_content(event["message"] + "\n宕机时长（秒）：" + str(round(event["downtime_seconds"])))
        with smtplib.SMTP(config.smtp_host, config.smtp_port, timeout=config.timeout) as smtp:
            smtp.ehlo()
            smtp.starttls(context=ssl.create_default_context())
            smtp.ehlo()
            if config.smtp_user:
                smtp.login(config.smtp_user, config.smtp_password)
            smtp.send_message(mail)
    else:
        raise ValueError("Unknown channel")

