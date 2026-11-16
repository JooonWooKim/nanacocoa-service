const shippingLabels = {
  BEFORE_SHIPPING: "출고 전", PREPARING: "배송 준비", ON_HOLD: "출고 보류",
  SHIPPED: "배송 중", DELIVERED: "배송 완료", CANCELED: "출고 전 취소",
};

function displayShippingTime(value) {
  return value?.replace("T", " ");
}
