(() => {
  'use strict';
  const page = document.querySelector('[data-recovery-kind]');
  if (!page) return;
  const reset = page.dataset.recoveryKind === 'reset';
  const form = page.querySelector('[data-recovery-form]');
  const message = page.querySelector('[data-recovery-message]');
  const button = form.querySelector('[type="submit"]');
  const inputs = Array.from(form.querySelectorAll('input'));
  let token = reset ? new URLSearchParams(window.location.hash.slice(1)).get('token') : null;
  if (reset) window.history.replaceState(null, '', window.location.pathname);
  let generation = 0, active = true, busy = false;
  let pendingPayload = null;
  const clearPayload = payload => {
    if (payload) { payload.token = null; payload.newPassword = null; payload.passwordConfirmation = null; payload.email = null; }
  };
  const current = value => active && value === generation;
  const clearInputs = () => inputs.forEach(input => { input.value = ''; });
  const missing = () => {
    if (reset && !token) {
      button.disabled = true;
      message.textContent = '복구 메일의 링크를 다시 열거나 복구 안내를 새로 요청해주세요.';
    }
  };
  if (reset && !/^[A-Za-z0-9_-]{43}$/.test(token || '')) token = null;
  missing();
  window.addEventListener('pagehide', () => {
    active = false; generation++; token = null; busy = false; clearInputs();
    clearPayload(pendingPayload); pendingPayload = null;
    message.textContent = ''; button.disabled = true;
  });
  window.addEventListener('pageshow', event => {
    if (event.persisted) {
      active = true; generation++; clearInputs(); button.disabled = reset; missing();
    }
  });
  async function csrf() {
    const response = await fetch('/api/auth/csrf', {credentials: 'same-origin', cache: 'no-store'});
    if (!response.ok) throw new Error('csrf');
    const body = await response.json();
    if (!body.data?.headerName || !body.data?.token) throw new Error('csrf');
    return body.data;
  }
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!active || busy || (reset && !token) || !form.reportValidity()) return;
    const payload = reset ? {token, newPassword: form.elements.newPassword.value,
      passwordConfirmation: form.elements.passwordConfirmation.value} : {email: form.elements.email.value.trim()};
    if (reset && (payload.newPassword !== payload.passwordConfirmation
        || new TextEncoder().encode(payload.newPassword).length > 72)) {
      message.textContent = '새 비밀번호와 확인 값을 확인해주세요. 비밀번호는 72바이트 이내여야 합니다.';
      return;
    }
    const requestGeneration = ++generation;
    pendingPayload = payload;
    busy = true; button.disabled = true; message.textContent = '요청을 처리하고 있습니다.';
    try {
      const security = await csrf();
      if (!current(requestGeneration)) return;
      const response = await fetch('/api/auth/password-recovery/' + (reset ? 'reset' : 'requests'), {
        method: 'POST', credentials: 'same-origin', cache: 'no-store',
        headers: {'Content-Type': 'application/json', [security.headerName]: security.token},
        body: JSON.stringify(payload)
      });
      if (!current(requestGeneration)) return;
      const body = await response.json();
      if (!current(requestGeneration)) return;
      if (response.status === 403 && body.code === 'CSRF_INVALID') {
        await csrf();
        if (!current(requestGeneration)) return;
        message.textContent = '보안 정보를 갱신했습니다. 내용을 확인하고 다시 제출해주세요.';
      } else if (response.ok) {
        message.textContent = reset ? '비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.'
          : '입력한 이메일로 복구 안내를 받을 수 있는 경우 메일이 발송됩니다.';
        if (reset) { token = null; clearInputs(); }
      } else {
        message.textContent = body.error || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.';
        if (reset && body.code === 'RECOVERY_01') { token = null; clearInputs(); }
      }
    } catch (error) {
      if (!current(requestGeneration)) return;
      message.textContent = reset ? '처리 결과를 확인할 수 없습니다. 새 비밀번호로 로그인하거나 복구 안내를 다시 요청해주세요.'
        : '요청 결과를 확인할 수 없습니다. 잠시 후 직접 다시 요청해주세요.';
      if (reset) { token = null; clearInputs(); }
    } finally {
      // 지연된 이전 요청은 새 화면의 잠금·안내를 변경하지 못한다.
      clearPayload(payload);
      if (pendingPayload === payload) pendingPayload = null;
      if (current(requestGeneration)) { busy = false; button.disabled = reset && !token; }
    }
  });
})();
