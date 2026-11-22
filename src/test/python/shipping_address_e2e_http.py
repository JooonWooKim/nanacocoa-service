"""격리 commerceE2e 환경의 합성 배송지 검증. 출력에 주소·연락처·세션을 기록하지 않는다."""
import json
import uuid
from commerce_e2e_http import Session

API = '/api/members/me/shipping-addresses'


def run():
    owner, other, admin = Session('other'), Session('buyer'), Session('admin')
    results = []
    def get(client, path): return client.call('GET', path)[1]['data']
    def book(): return get(owner, API)
    def key(): return {'Idempotency-Key': str(uuid.uuid4())}
    def details(label):
        return {'label': label, 'recipientName': '가상 수령인', 'phoneNumber': '010-0000-0000',
                'postalCode': '12345', 'basicAddress': '가상 주소', 'detailAddress': '합성 상세'}
    def create(label):
        payload = {'address': details(label), 'makeDefault': False, 'expectedRevision': book()['revision']}
        headers = key()
        return owner.call('POST', API, payload, headers=headers)[1]['data'], payload, headers
    before = get(admin, '/api/admin/e2e-snapshot')['providerCalls']
    for row in book()['items']:
        owner.call('DELETE', f"{API}/{row['id']}?expectedVersion={row['version']}&expectedRevision={book()['revision']}")
    first, original, headers = create('테스트 집')
    assert len(first['items']) == 1 and first['items'][0]['defaultAddress']
    owner.call('POST', API, original, headers=headers)
    assert len(book()['items']) == 1
    owner.call('POST', API, {**original, 'address': details('변경된 요청')}, headers=headers, expected=(409,))
    second, _, _ = create('테스트 회사')
    target = second['items'][1]
    state = owner.call('PUT', f"{API}/{target['id']}/default", {'expectedVersion': target['version'], 'expectedRevision': second['revision']})[1]['data']
    assert sum(i['defaultAddress'] for i in state['items']) == 1 and state['items'][1]['defaultAddress']
    owner.call('PUT', f"{API}/{target['id']}/default", {'expectedVersion': target['version'], 'expectedRevision': second['revision']}, expected=(409,))
    results.append({'scenario': 'registration-replay-default-version', 'result': 'PASS'})

    other.call('DELETE', f"{API}/{target['id']}?expectedVersion={target['version']}&expectedRevision={state['revision']}", expected=(404,))
    owner.call('DELETE', f"{API}/{target['id']}?expectedVersion={target['version']}&expectedRevision={state['revision']}", expected=(403,), csrf=False)
    owner.call('GET', '/api/members/me', expected=(403,))
    owner.call('PUT', f"{API}/{target['id']}/default", {'expectedVersion': target['version'], 'expectedRevision': state['revision']}, headers={'X-Checkout-Account': 'another-account'}, expected=(409,))
    results.append({'scenario': 'owner-csrf-account-profile-boundary', 'result': 'PASS'})

    row = state['items'][1]
    shipping = f"[{row['postalCode']}] {row['basicAddress']} {row['detailAddress']}"
    request = {'recipientName': row['recipientName'], 'phoneNumber': row['phoneNumber'], 'shippingAddress': shipping,
               'items': [{'productId': 1, 'quantity': 1}]}
    submission = key()
    saved = owner.call('POST', '/api/orders', request, headers=submission, expected=(201,))[1]['data']
    owner.call('PUT', f"{API}/{row['id']}", {'address': {**details('수정 회사'), 'basicAddress': '변경 가상 주소'}, 'expectedVersion': row['version'], 'expectedRevision': state['revision']})
    changed = next(i for i in book()['items'] if i['id'] == row['id'])
    owner.call('DELETE', f"{API}/{row['id']}?expectedVersion={changed['version']}&expectedRevision={book()['revision']}")
    assert not any(i['defaultAddress'] for i in book()['items'])
    assert get(owner, '/api/orders/' + saved['orderId'])['shippingAddress'] == shipping
    assert owner.call('POST', '/api/orders', request, headers=submission)[1]['data']['orderId'] == saved['orderId']
    results.append({'scenario': 'address-edit-delete-order-snapshot-replay', 'result': 'PASS'})

    owner.call('POST', '/api/auth/logout')
    owner = Session('other')
    assert len(book()['items']) == 1 and not book()['items'][0]['defaultAddress']
    results.append({'scenario': 'relogin-persists-no-default-promotion', 'result': 'PASS'})
    remaining = book()['items'][0]
    owner.call('DELETE', f"{API}/{remaining['id']}?expectedVersion={remaining['version']}&expectedRevision={book()['revision']}")
    owner.call('POST', API, original, headers=headers)
    assert book()['items'] == []
    assert get(admin, '/api/admin/e2e-snapshot')['providerCalls'] == before
    results.append({'scenario': 'deleted-registration-replay-and-no-provider-calls', 'result': 'PASS'})
    return results


if __name__ == '__main__':
    print(json.dumps(run(), ensure_ascii=False, indent=2))
