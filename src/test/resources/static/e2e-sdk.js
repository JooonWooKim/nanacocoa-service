// 테스트 서버의 checkout에서만 주입. 실제 외부 SDK와 결제창을 호출하지 않는다.
window.TossPayments = function () {
  return { payment() { return { async requestPayment(request) {
    const target = new URL(request.successUrl);
    target.searchParams.set('paymentKey', 'e2e_' + request.orderId);
    target.searchParams.set('orderId', request.orderId);
    target.searchParams.set('amount', String(request.amount.value));
    window.location.assign(target.href);
  } }; } };
};
