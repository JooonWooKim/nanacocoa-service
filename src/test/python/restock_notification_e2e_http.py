"""Verify restock notifications through the isolated commerceE2e HTTP flow."""
import time
import uuid

from commerce_e2e_http import Session


def run():
    buyer, admin, subscriber = Session("buyer"), Session("admin"), Session("other")
    product = 6
    endpoint = f"/api/restock-subscriptions/{product}"

    def get(session, path):
        return session.call("GET", path)[1]["data"]

    def key():
        return str(uuid.uuid4())

    def set_stock(quantity):
        inventory = next(row for row in get(admin, "/api/products/inventory") if row["id"] == product)
        admin.call("PUT", f"/api/products/{product}/inventory", {
            "stockQuantity": quantity, "saleStatus": "ON_SALE", "expectedVersion": inventory["stockVersion"],
        })

    def purchase():
        return buyer.call("POST", "/api/orders", {
            "recipientName": "가상 수령인", "phoneNumber": "010-0000-0000", "shippingAddress": "가상 주소",
            "items": [{"productId": product, "quantity": 1}],
        }, expected=(201,), headers={"Idempotency-Key": key()})[1]["data"]

    def notices():
        return [row for row in get(subscriber, "/api/notifications")["items"]
                if row.get("productId") == str(product)]

    set_stock(1)
    unpaid = purchase()
    subscriber.call("PUT", endpoint, expected=(403,), csrf=False)
    assert subscriber.call("PUT", endpoint)[1]["data"]["active"]
    buyer.call("POST", f"/api/orders/{unpaid['orderId']}/cancel")
    time.sleep(1.2)
    assert not notices() and get(subscriber, endpoint)["active"]

    order = purchase()
    oid = order["orderId"]
    buyer.call("POST", "/api/payments/confirm", {
        "orderId": oid, "amount": order["totalAmount"], "paymentKey": "e2e_" + oid,
    }, headers={"Idempotency-Key": key()}, expected=(200, 201))
    shipping = f"/api/admin/shipping/orders/{oid}"
    admin.call("POST", shipping, {"requestKey": key(), "revision": get(admin, shipping)["revision"], "action": "SHIP",
                                  "carrierCode": "test", "trackingNumber": "0012345678", "reason": "합성 출고"})
    rid = buyer.call("POST", f"/api/orders/{oid}/return-requests", {
        "requestKey": key(), "type": "RETURN", "reason": "합성 반품",
    })[1]["data"]["requests"][0]["id"]
    returns = f"/api/admin/return-requests/{rid}"

    def action(name, **kwargs):
        payload = {"requestKey": key(), "revision": get(admin, returns)["revision"],
                   "action": name, "reason": "합성 검증", **kwargs}
        return admin.call("POST", returns + "/actions", payload)

    for step in ("APPROVE", "COLLECT", "RECEIVE"):
        action(step)
    action("INSPECT", resellable=True, refundEligible=True)
    action("REFUND")
    assert not notices()
    action("RESTOCK")
    for _ in range(24):
        found = notices()
        if found:
            break
        time.sleep(0.5)
    assert len(found) == 1 and found[0]["type"] == "PRODUCT_RESTOCKED"
    assert not get(subscriber, endpoint)["active"]
    assert not any(row.get("productId") == str(product) for row in get(buyer, "/api/notifications")["items"])
    notice = found[0]["id"]
    buyer.call("PUT", f"/api/notifications/{notice}/read", expected=(404,))
    subscriber.call("PUT", f"/api/notifications/{notice}/read")
    print("PASS: reservation release excluded; inspected return restock emits one owner notification; scoped read")


if __name__ == "__main__":
    run()
