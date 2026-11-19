function setupSignupFields(form, isSubmitting) {
  const password = form.querySelector('[name="password"]');
  const confirmation = form.querySelector('[name="passwordConfirm"]');
  const confirmationStatus = form.querySelector('[data-password-confirm-status]');
  const postalCode = form.querySelector('[data-address-postal-code]');
  const basic = form.querySelector('[data-address-basic]');
  const detail = form.querySelector('[data-address-detail]');
  const search = form.querySelector('[data-address-search]');
  const address = form.querySelector('[name="address"]');
  const middle = form.querySelector('[data-phone-middle]');
  const last = form.querySelector('[data-phone-last]');
  const phone = form.querySelector('[name="phoneNumber"]');

  function validatePassword() {
    const mismatch = Boolean(confirmation.value) && confirmation.value !== password.value;
    confirmation.setCustomValidity(mismatch ? '비밀번호가 일치하지 않습니다.' : '');
    confirmationStatus.textContent = mismatch ? '비밀번호가 일치하지 않습니다.' : '';
    confirmationStatus.hidden = !mismatch;
  }
  password.addEventListener('input', validatePassword);
  confirmation.addEventListener('input', validatePassword);
  form.querySelectorAll('[data-password-toggle]').forEach(button => {
    button.addEventListener('click', () => {
      const input = form.querySelector(`[name="${button.dataset.passwordToggle}"]`);
      const visible = input.type === 'password';
      input.type = visible ? 'text' : 'password';
      button.setAttribute('aria-pressed', String(visible));
      button.setAttribute('aria-label', `${input === password ? '비밀번호' : '비밀번호 확인'} ${visible ? '숨김' : '표시'}`);
    });
  });
  function updateAddress() {
    address.value = formatShippingAddress(postalCode.value, basic.value, detail.value);
    detail.setCustomValidity(address.value.length > 500 ? '우편번호와 상세주소를 포함한 전체 주소는 500자 이하로 입력해 주세요.' : '');
  }
  setupKakaoAddressSearch({
    postalCodeInput: postalCode, basicAddressInput: basic, detailAddressInput: detail,
    searchButton: search, statusElement: form.querySelector('[data-address-search-status]'),
    onAddressChange: updateAddress, shouldKeepDisabled: isSubmitting,
  });
  function updatePhone() { phone.value = `010-${middle.value}-${last.value}`; }
  middle.addEventListener('input', updatePhone);
  last.addEventListener('input', updatePhone);
  return {
    validate() {
      validatePassword(); updateAddress(); updatePhone();
      postalCode.setCustomValidity(/^[0-9]{5}$/.test(postalCode.value.trim()) ? '' : '우편번호 5자리를 입력해 주세요.');
      basic.setCustomValidity(basic.value.trim() ? '' : '기본주소를 입력해 주세요.');
      return form.reportValidity();
    },
    setBusy(busy) { search.disabled = busy || typeof window.kakao?.Postcode !== 'function'; },
  };
}
