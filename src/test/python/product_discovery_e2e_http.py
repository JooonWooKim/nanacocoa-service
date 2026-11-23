"""Exercise real HTTP on the synthetic commerceE2e server only."""
import json
import urllib.parse
import urllib.request
import uuid
from commerce_e2e_http import BASE, Session


def search(**params):
    with urllib.request.urlopen(BASE + "/api/products/search?" + urllib.parse.urlencode(params), timeout=10) as response:
        return json.load(response)["data"]


def run():
    admin, buyer = Session("admin"), Session("buyer")
    fixture = "탐색 " + uuid.uuid4().hex[:8]
    category = admin.call("POST", "/api/products/admin/categories", {"name": fixture}, expected=(201,))[1]["data"]
    category_id = category["id"]
    endpoint = f"/api/products/admin/categories/{category_id}"
    ids = []
    for index in range(25):
        payload = {"name": f"{fixture} Mug%_{index:02}", "price": 41000, "summary": "가상 상품",
                   "detailTitle": "가상 상세", "description": "검증 전용", "categoryId": category_id}
        ids.append(admin.call("POST", "/api/products", payload, expected=(201,))[1]["data"]["id"])
    result = search(q=f" {fixture} mUG%_ ", category=category_id)
    assert result["totalItems"] == 25 and result["totalPages"] == 2
    assert len(result["items"]) == 24 and result["items"][0]["id"] == ids[-1]
    assert search(q=fixture, category=category_id, page=2)["items"][0]["id"] == ids[0]
    assert all("stockQuantity" not in item for item in result["items"])
    print("PASS DISCOVERY-HTTP-01: literal search, category and page boundaries")

    buyer.call("PATCH", endpoint, {"visible": False}, expected=(403,))
    admin.call("PATCH", endpoint, {"visible": False}, csrf=False, expected=(403,))
    admin.call("PATCH", endpoint, {"visible": False})
    admin.call("GET", f"/api/products/search?category={category_id}", expected=(404,))
    assert search(q=fixture)["totalItems"] == 25
    admin.call("PUT", "/api/products/admin/1/category", {"categoryId": category_id}, expected=(404,))
    admin.call("PATCH", endpoint, {"visible": True, "name": fixture + " 복구"})
    assert search(q=fixture, category=category_id)["totalItems"] == 25
    print("PASS DISCOVERY-HTTP-02: admin and CSRF, hidden assignment denied, restoration preserves products")
    print(f"Browser fixture category ID {category_id}")


if __name__ == "__main__":
    run()
