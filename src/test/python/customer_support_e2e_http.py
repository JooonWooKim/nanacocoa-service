"""Support flows against the synthetic commerceE2e loopback server."""
import json
import urllib.request
import uuid

from commerce_e2e_http import BASE, Session


def run():
    with urllib.request.urlopen(BASE + "/api/support/faq", timeout=10) as response:
        faq = json.load(response)["data"]
    assert any(item.get("policyHref") == "policies.html?document=SHIPPING_RETURN" for item in faq)
    print("PASS SUPPORT-HTTP-01: anonymous FAQ and approved-policy route")

    owner, other, admin = Session("buyer"), Session("other"), Session("admin")
    order = owner.call("POST", "/api/orders", {
        "recipientName": "가상 회원", "phoneNumber": "010-0000-0000", "shippingAddress": "합성 주소",
        "items": [{"productId": 1, "quantity": 1}],
    }, headers={"Idempotency-Key": str(uuid.uuid4())}, expected=(201,))[1]["data"]
    api = "/api/support/inquiries"
    operations = "/api/admin/support/inquiries"
    data = {"title": "HTTP 문의", "body": "합성 문의 내용", "orderId": order["orderId"]}
    headers = {"Idempotency-Key": str(uuid.uuid4())}
    other.call("POST", api, data, headers=headers, expected=(404,))
    owner.call("POST", api, data, headers=headers, expected=(403,), csrf=False)
    saved = owner.call("POST", api, data, headers=headers, expected=(201,))[1]["data"]
    repeated = owner.call("POST", api, data, headers=headers)[1]["data"]
    assert repeated["id"] == saved["id"] and repeated["orderId"] == order["orderId"]
    owner.call("POST", api, dict(data, title="변경 문의"), headers=headers, expected=(409,))
    detail = f"{api}/{saved['id']}"
    other.call("GET", detail, expected=(404,))
    assert owner.call("GET", detail)[1]["data"]["status"] == "PENDING"
    print("PASS SUPPORT-HTTP-02: optional order ownership, replay and private detail")

    answer_url = f"{operations}/{saved['id']}/reply"
    owner.call("POST", answer_url, {"body": "권한 없는 답변"}, expected=(403,))
    admin.call("POST", answer_url, {"body": "합성 운영자 답변"})
    admin.call("POST", answer_url, {"body": "중복 답변"}, expected=(409,))
    result = owner.call("GET", detail)[1]["data"]
    assert result["status"] == "ANSWERED" and result["replyBody"] == "합성 운영자 답변"
    state = owner.call("GET", "/api/orders/" + order["orderId"])[1]["data"]
    assert state["status"] == "CREATED"
    print("PASS SUPPORT-HTTP-03: operator answer once, owner sees reply, order unchanged")


if __name__ == "__main__":
    run()
