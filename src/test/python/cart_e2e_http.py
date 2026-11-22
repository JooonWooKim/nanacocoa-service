"""commerceE2e loopback 환경의 회원 장바구니 HTTP 검증. 합성 데이터만 사용한다."""
import json
import time
import uuid
from commerce_e2e_http import Session


def run():
    owner, admin, stranger = Session('other'), Session('admin'), Session('buyer')
    evidence = []
    def get(s, path): return s.call('GET', path)[1]['data']
    def cart(): return get(owner, '/api/cart')
    def key(): return {'Idempotency-Key': str(uuid.uuid4())}
    def add(product, quantity=1, headers=None):
        return owner.call('POST', '/api/cart/items', {'productId': product, 'quantity': quantity}, headers=headers or key())[1]['data']
    def order(rows):
        payload = {'recipientName': '가상 수령인', 'phoneNumber': '010-0000-0000', 'shippingAddress': '가상 주소',
                   'items': [{'productId': i['productId'], 'quantity': i['quantity']} for i in rows],
                   'cartItems': [{'itemId': i['itemId'], 'expectedVersion': i['version'], 'quantity': i['quantity'], 'expectedUnitPrice': i['unitPrice']} for i in rows]}
        headers = key()
        result = owner.call('POST', '/api/orders', payload, headers=headers, expected=(201,))[1]['data']
        return result, payload, headers
    def approve(o, expected=(200,)):
        payload = {'orderId': o['orderId'], 'paymentKey': 'e2e_' + o['orderId'], 'amount': o['totalAmount']}
        headers = key()
        result = owner.call('POST', '/api/payments/confirm', payload, headers=headers, expected=expected)[1]['data'] if expected == (200,) else owner.call('POST', '/api/payments/confirm', payload, headers=headers, expected=expected)[1].get('data')
        return result, payload, headers
    for row in cart()['items']:
        owner.call('DELETE', f"/api/cart/items/{row['itemId']}?expectedVersion={row['version']}")
    headers = key()
    add(1, 2, headers); add(1, 2, headers); add(2); add(3)
    assert cart()['totalQuantity'] == 4
    a = cart()['items'][0]
    owner.call('PUT', f"/api/cart/items/{a['itemId']}", {'quantity': 3, 'expectedVersion': a['version']})
    owner.call('PUT', f"/api/cart/items/{a['itemId']}", {'quantity': 4, 'expectedVersion': a['version']}, expected=(409,))
    stranger.call('DELETE', f"/api/cart/items/{a['itemId']}?expectedVersion=0", expected=(404,))
    owner.call('DELETE', f"/api/cart/items/{a['itemId']}?expectedVersion=0", expected=(403,), csrf=False)
    evidence.append({'scenario': 'storage-replay-version-owner-csrf', 'result': 'PASS'})

    selected = cart()['items'][:2]
    o, payload, submission = order(selected)
    assert len(cart()['items']) == 3
    assert owner.call('POST', '/api/orders', payload, headers=submission)[1]['data']['orderId'] == o['orderId']
    result, confirm, confirmation = approve(o)
    assert result['status'] == 'SUCCESS'
    assert [i['productId'] for i in cart()['items']] == [3]
    assert owner.call('POST', '/api/orders', payload, headers=submission)[1]['data']['orderId'] == o['orderId']
    owner.call('POST', '/api/payments/confirm', confirm, headers=confirmation)
    state = get(admin, '/api/admin/e2e-snapshot')
    assert state['providerCalls'].get('confirm:' + o['orderId']) == 1
    assert any(i['order_id'] == o['orderId'] and i['stock_state'] == 'SOLD' for i in state['orders'])
    evidence.append({'scenario': 'multi-order-approval-cleanup-replay-db-pg', 'result': 'PASS'})

    declined, _, _ = order(cart()['items'])
    approve(declined, expected=(422,))
    assert cart()['totalQuantity'] == 1
    evidence.append({'scenario': 'declined-approval-retains-cart', 'result': 'PASS'})

    row = add(1, 2)['items'][-1]
    # Select by product rather than relying on row order.
    row = next(i for i in cart()['items'] if i['productId'] == 1)
    modified, _, _ = order([row])
    add(1)
    approve(modified)
    assert next(i for i in cart()['items'] if i['productId'] == 1)['quantity'] == 3
    evidence.append({'scenario': 'post-order-cart-edit-preserved', 'result': 'PASS'})

    owner.call('POST', '/api/auth/logout')
    owner = Session('other')
    assert get(owner, '/api/cart')['totalQuantity'] == 4
    evidence.append({'scenario': 'logout-login-persists', 'result': 'PASS'})
    return evidence


if __name__ == '__main__':
    print(json.dumps(run(), ensure_ascii=False, indent=2))
