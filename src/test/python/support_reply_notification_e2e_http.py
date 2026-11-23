"""Verify private support reply notifications on the synthetic loopback app."""
import time
import uuid

from commerce_e2e_http import Session


def run():
    owner, other, admin = Session("buyer"), Session("other"), Session("admin")
    inquiries = "/api/support/inquiries"
    alerts = "/api/notifications"
    answer = "합성 문의 답변입니다."
    inquiry = owner.call("POST", inquiries, {"title": "답변 알림 검증", "body": "합성 문의"},
                         headers={"Idempotency-Key": str(uuid.uuid4())}, expected=(201,))[1]["data"]
    inquiry_id = str(inquiry["id"])
    reply = f"/api/admin/support/inquiries/{inquiry_id}/reply"
    admin.call("POST", reply, {"body": answer})
    admin.call("POST", reply, {"body": answer}, expected=(409,))
    for _ in range(30):
        inbox = owner.call("GET", alerts)[1]["data"]
        found = [row for row in inbox["items"] if row.get("inquiryId") == inquiry_id]
        if found:
            break
        time.sleep(0.5)
    assert len(found) == 1
    notice = found[0]
    assert notice["type"] == "SUPPORT_ANSWERED"
    assert not {"title", "replyBody", "body", "orderId"} & notice.keys()
    detail = f"{inquiries}/{inquiry_id}"
    other.call("GET", detail, expected=(404,))
    assert owner.call("GET", detail)[1]["data"]["replyBody"] == answer
    read = f"{alerts}/{notice['id']}/read"
    other.call("PUT", read, expected=(404,))
    owner.call("PUT", read, expected=(403,), csrf=False)
    timestamp = owner.call("PUT", read)[1]["data"]["readAt"]
    assert timestamp and owner.call("PUT", read)[1]["data"]["readAt"] == timestamp
    print("PASS: private reply notification, duplicate prevention, owner detail, CSRF and read replay")


if __name__ == "__main__":
    run()
