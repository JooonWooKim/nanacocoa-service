"""Loopback RecoveryE2eServer only; emits status evidence without credentials/tokens."""
import http.cookiejar
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:18086"


class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, path, body=None, secure=True):
        headers = {}
        if body is not None:
            headers["Content-Type"] = "application/json"
            if secure:
                _, csrf, _ = self.call("/api/auth/csrf")
                headers[csrf["data"]["headerName"]] = csrf["data"]["token"]
        request = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
        try:
            response = self.opener.open(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        data = response.read().decode()
        return response.status, json.loads(data) if "application/json" in response.headers.get("Content-Type", "") else data, response.headers


def main():
    account = {"email": "buyer@example.test", "password": "BeforeRecovery123!"}
    a, b, anonymous = Client(), Client(), Client()
    assert a.call("/api/auth/login", account)[0] == 200
    assert b.call("/api/auth/login", account)[0] == 200
    known = anonymous.call("/api/auth/password-recovery/requests", {"email": account["email"]})
    unknown = anonymous.call("/api/auth/password-recovery/requests", {"email": "absent@example.test"})
    assert known[0] == unknown[0] == 202 and known[1] == unknown[1]
    assert known[2].get("Cache-Control") == "no-store"
    print("PASS HTTP-RECOVERY-01: anonymous request equality and no-store")
    link = None
    for _ in range(100):
        text = anonymous.call("/api/auth/recovery-fixture")[1]
        links = re.findall(r'<a href="([^"]+)">HTTP', text)
        if links:
            link = links[-1]
            break
        time.sleep(0.02)
    assert link and urllib.parse.urlsplit(link).netloc == "127.0.0.1:18086"
    raw = urllib.parse.parse_qs(urllib.parse.urlsplit(link).fragment)["token"][0]
    page = anonymous.call("/password-reset.html")
    assert page[0] == 200 and page[2].get("Referrer-Policy") == "no-referrer"
    reset = {"token": raw, "newPassword": "AfterRecovery123!", "passwordConfirmation": "AfterRecovery123!"}
    assert anonymous.call("/api/auth/password-recovery/reset", reset, secure=False)[0] == 403
    assert anonymous.call("/api/auth/password-recovery/reset", reset)[0] == 200
    reused = anonymous.call("/api/auth/password-recovery/reset", reset)
    assert reused[0] == 400 and reused[1]["code"] == "RECOVERY_01"
    print("PASS HTTP-RECOVERY-02: mock mail link, GET safety, CSRF and one-time reset")
    assert a.call("/api/cart")[0] == b.call("/api/cart")[0] == 401
    assert a.call("/api/auth/session")[1]["authenticated"] is False
    print("PASS HTTP-RECOVERY-03: two independent previous sessions rejected")
    assert a.call("/api/auth/login", account)[0] == 400
    account["password"] = "AfterRecovery123!"
    assert a.call("/api/auth/login", account)[0] == 200
    assert a.call("/api/cart")[0] == 200
    assert a.call("/api/members/me/shipping-addresses")[0] == 200
    print("PASS HTTP-RECOVERY-04: old password rejected, new login and cart/address reads preserved")


if __name__ == "__main__":
    main()
