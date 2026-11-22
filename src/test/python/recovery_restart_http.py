"""Run against fresh MySQL RecoveryE2eServer; restart that server at READY, then press Enter.

The recovery token and cookies remain only in this process's memory.
"""
import re
import time
import urllib.parse
from recovery_e2e_http import Client


def main():
    client = Client()
    assert client.call("/api/auth/login", {"email": "buyer@example.test", "password": "BeforeRecovery123!"})[0] == 200
    started = time.monotonic()
    assert client.call("/api/auth/password-recovery/requests", {"email": "buyer@example.test"})[0] == 202
    link = None
    for _ in range(100):
        found = re.findall(r'<a href="([^"]+)">HTTP', client.call("/api/auth/recovery-fixture")[1])
        if found:
            link = found[-1]
            break
        time.sleep(0.02)
    assert link
    token = urllib.parse.parse_qs(urllib.parse.urlsplit(link).fragment)["token"][0]
    reset = {"token": token, "newPassword": "AfterRecovery123!", "passwordConfirmation": "AfterRecovery123!"}
    assert client.call("/api/auth/password-recovery/reset", reset)[0] == 200
    print("READY: reset committed; restart only the loopback Java server, then press Enter", flush=True)
    input()
    assert time.monotonic() - started < 60, "Repeat with a fresh runtime DB to test the 60-second email limit"
    assert client.call("/api/cart")[0] == 401
    assert client.call("/api/auth/password-recovery/reset", reset)[0] == 400
    assert client.call("/api/auth/login", {"email": "buyer@example.test", "password": "AfterRecovery123!"})[0] == 200
    assert "HTTP" not in client.call("/api/auth/recovery-fixture")[1]
    assert client.call("/api/auth/password-recovery/requests", {"email": "buyer@example.test"})[0] == 202
    time.sleep(0.2)
    assert "HTTP" not in client.call("/api/auth/recovery-fixture")[1]
    print("PASS RESTART-RECOVERY: password/token consumption/session denial/email limit survive Java process restart")


if __name__ == "__main__":
    main()
