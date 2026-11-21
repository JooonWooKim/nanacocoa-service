"""실행 중인 commerceE2e 서버의 실제 HTTP·세션·CSRF·DB 대조. 외부 주소는 허용하지 않는다."""
import http.cookiejar
import json
import time
import urllib.error
import urllib.request
import uuid

BASE = 'http://127.0.0.1:18082'

class Session:
    def __init__(self, role):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = None
        self.call('POST', '/api/auth/login', {'email': role+'@example.test', 'password': 'E2eFixture123!'})
        self.csrf = None  # 로그인으로 회전한 토큰을 다시 조회한다.

    def call(self, method, path, data=None, expected=(200,), headers=None, csrf=True):
        assert path.startswith('/api/')
        h = {'Content-Type': 'application/json', **(headers or {})}
        if method != 'GET' and csrf:
            if self.csrf is None:
                self.csrf = self.call('GET', '/api/auth/csrf')[1]['data']
            h[self.csrf['headerName']] = self.csrf['token']
        request = urllib.request.Request(BASE+path, data=json.dumps(data).encode() if data is not None else None,
                                         headers=h, method=method)
        try:
            response = self.http.open(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        body = json.loads(response.read() or '{}')
        assert response.status in expected, (method, path.split('?')[0], response.status, expected, body.get('code'))
        return response.status, body


def run():
    buyer, admin, other = Session('buyer'), Session('admin'), Session('other')
    evidence = []
    def get(s, path): return s.call('GET', path)[1]['data']
    def snapshot(): return get(admin, '/api/admin/e2e-snapshot')
    def action(rid, name, **extra):
        row=get(admin, f'/api/admin/return-requests/{rid}')
        payload={'requestKey':str(uuid.uuid4()),'revision':row['revision'],'action':name,'reason':'가상 검증',**extra}
        return admin.call('POST',f'/api/admin/return-requests/{rid}/actions',payload,expected=(200,202,422))
    def wait_recovery(kind, ident):
        for _ in range(20):
            code, body=admin.call('POST',f'/api/admin/payment-operations/{kind}/{ident}/reconcile',expected=(200,202,409))
            if code==200: return body['data']
            time.sleep(2)
        raise AssertionError('복구 대기 초과')
    for product in range(1,7):
        before=snapshot()
        payload={'recipientName':'가상 수령인','phoneNumber':'010-0000-0000','shippingAddress':'가상 주소',
                 'items':[{'productId':product,'quantity':1}]}
        headers={'Idempotency-Key':str(uuid.uuid4())}
        code, body=buyer.call('POST','/api/orders',payload,expected=(201,),headers=headers)
        order=body['data']; oid=order['orderId']; amount=order['totalAmount']
        assert buyer.call('POST','/api/orders',payload,headers=headers)[1]['data']['orderId']==oid
        other.call('GET','/api/orders/'+oid,expected=(404,))
        buyer.call('GET','/api/admin/e2e-snapshot',expected=(403,))
        buyer.call('POST','/api/orders/'+oid+'/cancel',expected=(403,),csrf=False)
        confirm_headers={'Idempotency-Key':str(uuid.uuid4())}
        confirm={'orderId':oid,'paymentKey':'e2e_'+oid,'amount':amount}
        if product==3:
            buyer.call('POST','/api/payments/confirm',confirm,headers=confirm_headers,expected=(422,))
            assert get(buyer,'/api/orders/'+oid)['status']!='PAID'
            rejected=snapshot()
            assert rejected['inventory'][product-1]==before['inventory'][product-1]
            evidence.append({'scenario':'approval-rejected','orderId':oid,'result':'PASS'})
            continue
        code, body=buyer.call('POST','/api/payments/confirm',confirm,headers=confirm_headers,expected=(200,201,202))
        if product==2:
            pending=get(admin,'/api/admin/payment-operations?kind=VERIFY_APPROVAL')['items']
            row=next(x for x in pending if x['orderId']==oid)
            wait_recovery('APPROVAL',row['id'])
        buyer.call('POST','/api/payments/confirm',confirm,headers=confirm_headers,expected=(200,201))
        if product==6:
            path='/api/admin/shipping/orders/'+oid
            ship=get(admin,path)
            admin.call('POST',path,{'requestKey':str(uuid.uuid4()),'revision':ship['revision'],'action':'SHIP',
                'carrierCode':'test','trackingNumber':'0012345678','reason':'가상 출고'})
        request={'requestKey':str(uuid.uuid4()),'type':'RETURN' if product==6 else 'CANCELLATION','reason':'가상 신청'}
        path=f'/api/orders/{oid}/return-requests'
        row=buyer.call('POST',path,request)[1]['data']['requests'][0]; rid=row['id']
        assert buyer.call('POST',path,request)[1]['data']['requests'][0]['id']==rid
        action(rid,'APPROVE')
        if product==6:
            action(rid,'COLLECT'); action(rid,'RECEIVE')
        action(rid,'INSPECT',resellable=product!=6,refundEligible=True)
        status, body=action(rid,'REFUND')
        if product==4:
            row=get(admin,f'/api/admin/return-requests/{rid}')
            assert row['refundStatus']=='PENDING'
            assert snapshot()['inventory'][product-1]['stock_quantity']==before['inventory'][product-1]['stock_quantity']-1
            wait_recovery('CANCELLATION',row['cancellationId'])
        if product==5:
            assert get(admin,f'/api/admin/return-requests/{rid}')['refundStatus']=='FAILED'
            action(rid,'REFUND')
        row=get(admin,f'/api/admin/return-requests/{rid}')
        assert row['refundStatus']=='SUCCESS'
        if product!=6:
            payload={'requestKey':str(uuid.uuid4()),'revision':row['revision'],'action':'RESTOCK','reason':'가상 재입고'}
            admin.call('POST',f'/api/admin/return-requests/{rid}/actions',payload)
            admin.call('POST',f'/api/admin/return-requests/{rid}/actions',payload)
            assert get(buyer,'/api/orders/'+oid)['returns']['requests'][0]['restockStatus']=='COMPLETED'
        else:
            assert 'RESTOCK' not in row['actions']
            admin.call('POST','/api/orders/'+oid+'/restock',{'reason':'우회'},expected=(409,))
        after=snapshot()
        assert after['inventory'][product-1]['stock_quantity']==before['inventory'][product-1]['stock_quantity']-(1 if product==6 else 0)
        assert after['inventory'][product-1]['reserved_quantity']==before['inventory'][product-1]['reserved_quantity']
        assert after['providerCalls']['cancel:'+oid]==(2 if product==5 else 1)
        assert after['providerCalls']['confirm:'+oid]==1
        assert get(buyer,'/api/orders/'+oid)['returns']['requests'][0]['refundStatus']=='SUCCESS'
        evidence.append({'scenario':product,'orderId':oid,'requestId':rid,'result':'PASS'})
    print(json.dumps({'scenarios':evidence,'snapshot':snapshot()},ensure_ascii=False,indent=2))

if __name__ == '__main__':
    run()
