"""Verify order notifications for completed synthetic commerceE2e scenarios."""
import json
import sys
import time
from commerce_e2e_http import Session


def run(path):
    with open(path, encoding="utf-8") as evidence:
        commerce = json.load(evidence)
    buyer, other, admin = Session("buyer"), Session("other"), Session("admin")
    expected = {}
    for case in commerce["scenarios"]:
        scenario = case["scenario"]
        types = set() if scenario == "approval-rejected" else {"PAYMENT_COMPLETED", "REFUND_COMPLETED"}
        if scenario == 6:
            types.add("SHIPPED")
        expected[case["orderId"]] = types
    for _ in range(30):
        data = buyer.call("GET", "/api/notifications")[1]["data"]
        hits = [row for row in data["items"] if row.get("orderId") in expected]
        if len(hits) == sum(map(len, expected.values())):
            break
        time.sleep(0.5)
    actual = {(row["orderId"], row["type"]) for row in hits}
    assert len(actual) == len(hits) and actual == {(order, kind) for order, kinds in expected.items() for kind in kinds}
    unread = data["unreadCount"]
    assert not data["hasNext"] and unread == sum(not row["readAt"] for row in data["items"])
    row = next((item for item in hits if not item["readAt"]), hits[0])
    read = f"/api/notifications/{row['id']}/read"
    other.call("PUT", read, expected=(404,))
    other_items = other.call("GET", "/api/notifications")[1]["data"]["items"]
    assert all(item.get("orderId") not in expected for item in other_items)
    buyer.call("PUT", read, expected=(403,), csrf=False)
    first = buyer.call("PUT", read)[1]["data"]["readAt"]
    assert first and buyer.call("PUT", read)[1]["data"]["readAt"] == first
    assert buyer.call("GET", "/api/notifications")[1]["data"]["unreadCount"] == unread - int(not row["readAt"])
    assert buyer.call("GET", "/api/orders/" + row["orderId"])[1]["data"]["orderId"] == row["orderId"]
    assert admin.call("GET", "/api/admin/e2e-snapshot")[1]["data"]["providerCalls"] == commerce["snapshot"]["providerCalls"]
    print(f"PASS: {len(hits)} unique confirmed notifications, approval rejection excluded, owner read and order link verified, PG call counts unchanged")


if __name__ == "__main__":
    run(sys.argv[1])
