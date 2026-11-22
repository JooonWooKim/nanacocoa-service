/* 주소록은 회원정보와 독립적이다. 원문 주소는 브라우저 저장소에 기록하지 않는다. */
window.NanacocoaShippingAddresses = (() => {
  const api = '/api/members/me/shipping-addresses';
  const fields = ['label', 'recipientName', 'phoneNumber', 'postalCode', 'basicAddress', 'detailAddress'];

  async function currentSession(account) {
    authSessionPromise = null;
    const session = await loadAuthSession();
    if (session.loadFailed) throw new Error('로그인 상태를 확인하지 못했습니다. 다시 조회해 주세요.');
    if (!session.authenticated || (account && account !== session.checkoutAccount)) {
      const error = new Error('로그인이 만료되었거나 계정이 변경되었습니다. 다시 로그인해 주세요.');
      error.accountChanged = true;
      throw error;
    }
    return session;
  }
  async function request(account, suffix = '', options = {}) {
    await currentSession(account);
    const response = await requestAuth(api + suffix, {
      ...options, cache: 'no-store',
      headers: { ...options.headers, 'X-Checkout-Account': account },
    });
    const body = await readJson(response);
    if (!response.ok || !body.data) {
      const error = new Error(body.error || '배송지 처리 결과를 확인하지 못했습니다. 목록을 다시 조회해 주세요.');
      error.status = response.status;
      error.accountChanged = response.status === 401 || body.code === 'ORDER_06';
      throw error;
    }
    if (body.data.checkoutAccount !== account) {
      const error = new Error('계정이 변경되었습니다. 다시 로그인해 주세요.');
      error.accountChanged = true; throw error;
    }
    return body.data;
  }
  function loginMessage(node, message) {
    node.replaceChildren(document.createTextNode(message + ' '));
    const link = document.createElement('a');
    link.href = 'login.html?return=shipping-addresses'; link.textContent = '로그인하기'; node.append(link);
  }
  function protectHistory(clear) {
    window.addEventListener('pagehide', clear);
    window.addEventListener('pageshow', event => { if (event.persisted) window.location.reload(); });
  }

  async function manage() {
    const page = document.querySelector('[data-shipping-address-page]');
    if (!page) return;
    const form = page.querySelector('[data-address-form]');
    const list = page.querySelector('[data-address-list]');
    const message = page.querySelector('[data-address-message]');
    const refreshButton = page.querySelector('[data-address-refresh]');
    const newButton = page.querySelector('[data-address-new]');
    const defaultInput = form.elements.makeDefault;
    const save = form.querySelector('[type="submit"]');
    let session, book, draft = null, pending = null, busy = false;

    function clearPrivate() {
      list.replaceChildren(); fields.forEach(name => { form.elements[name].value = ''; });
      book = null; draft = null; pending = null; form.hidden = true;
    }
    protectHistory(clearPrivate);
    function lock(value) {
      busy = value;
      page.querySelectorAll('input,button').forEach(control => { control.disabled = value; });
      if (!value && draft) defaultInput.disabled = true;
      if (!value && book) [...list.children].forEach((card, index) => {
        card.querySelectorAll('button').forEach(button => {
          if (button.textContent === '기본으로 지정') button.disabled = Boolean(book.items[index]?.defaultAddress);
        });
      });
    }
    function start(row = null) {
      if (!book || busy) return;
      pending = null; draft = row ? { id: row.id, version: row.version, revision: book.revision } : null;
      fields.forEach(name => { form.elements[name].value = row?.[name] || ''; });
      defaultInput.checked = row ? row.defaultAddress : !book.items.length;
      defaultInput.disabled = Boolean(row);
      save.textContent = row ? '배송지 수정' : '배송지 등록';
      form.hidden = false;
      message.textContent = row ? '수정할 내용을 입력해 주세요. 기본 지정은 목록에서 변경합니다.' : '새 배송지를 입력해 주세요. 회원정보의 주소는 변경하지 않습니다.';
    }
    function render() {
      list.replaceChildren();
      if (!book.items.length) {
        const empty = document.createElement('p'); empty.textContent = '등록한 배송지가 없습니다.'; list.append(empty);
      }
      for (const row of book.items) {
        const card = document.createElement('article'); card.className = 'checkout-card';
        const heading = document.createElement('h2'); heading.textContent = row.label + (row.defaultAddress ? ' · 기본 배송지' : '');
        const recipient = document.createElement('p'); recipient.textContent = `${row.recipientName} · ${row.phoneNumber}`;
        const address = document.createElement('p'); address.textContent = `[${row.postalCode}] ${row.basicAddress} ${row.detailAddress}`.trim();
        const edit = document.createElement('button'); edit.type = 'button'; edit.textContent = '수정'; edit.onclick = () => start(row);
        const designate = document.createElement('button'); designate.type = 'button'; designate.textContent = '기본으로 지정'; designate.disabled = row.defaultAddress;
        designate.onclick = () => mutate(`/${row.id}/default`, { method: 'PUT', body: JSON.stringify({ expectedVersion: row.version, expectedRevision: book.revision }) });
        const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '삭제';
        remove.onclick = () => mutate(`/${row.id}?expectedVersion=${row.version}&expectedRevision=${book.revision}`, { method: 'DELETE' });
        card.append(heading, recipient, address, edit, designate, remove); list.append(card);
      }
    }
    async function reload() {
      book = await request(session.checkoutAccount); render();
    }
    async function mutate(suffix, options, savedForm = false) {
      if (busy) return; lock(true);
      try {
        book = await request(session.checkoutAccount, suffix, options);
        pending = null;
        if (savedForm) { draft = null; form.hidden = true; }
        render(); message.textContent = '배송지를 저장했습니다.';
        // 다른 목록 작업으로 편집 문맥이 낡아도 기존 draft revision은 유지한다.
      } catch (error) {
        if (error.accountChanged) { clearPrivate(); loginMessage(message, error.message); return; }
        if ([400, 404, 409].includes(error.status)) pending = null;
        message.textContent = error.message + ' 입력은 보존했습니다. 목록에서 수정 대상을 다시 선택하거나 새 입력을 시작해 주세요.';
        try { await reload(); } catch (lookupError) {
          if (lookupError.accountChanged) { clearPrivate(); loginMessage(message, lookupError.message); }
          else { list.replaceChildren(); book = null; }
        }
      } finally {
        lock(false);

      }
    }
    newButton.onclick = () => start();
    refreshButton.onclick = async () => {
      if (busy) return; lock(true);
      try { if (!session) session = await currentSession(); await reload(); message.textContent = '배송지 목록을 조회했습니다. 입력 내용은 유지했습니다.'; }
      catch (error) { if (error.accountChanged) { clearPrivate(); loginMessage(message, error.message); } else message.textContent = error.message; }
      finally { lock(false); }
    };
    form.addEventListener('submit', event => {
      event.preventDefault();
      if (busy || !book || !form.reportValidity()) return;
      const address = Object.fromEntries(fields.map(name => [name, form.elements[name].value.trim()]));
      const combined = formatShippingAddress(address.postalCode, address.basicAddress, address.detailAddress);
      if (combined.length > 500) { message.textContent = '우편번호·상세주소를 포함한 전체 주소는 500자 이하로 입력해 주세요.'; return; }
      if (draft) {
        return mutate(`/${draft.id}`, { method: 'PUT', body: JSON.stringify({ address, expectedVersion: draft.version, expectedRevision: draft.revision }) }, true);
      }
      const content = JSON.stringify({ address, makeDefault: defaultInput.checked });
      if (pending && pending.content !== content) {
        message.textContent = '이전 등록 결과가 불확실합니다. 목록을 확인한 뒤 새 배송지 입력으로 시작해 주세요.'; return;
      }
      try {
        pending ||= { content, key: randomUuid(), body: JSON.stringify({ address, makeDefault: defaultInput.checked, expectedRevision: book.revision }) };
      } catch (error) { message.textContent = error.message || '등록 식별자를 만들지 못했습니다. 브라우저를 확인해 주세요.'; return; }
      return mutate('', { method: 'POST', headers: { 'Idempotency-Key': pending.key }, body: pending.body }, true);
    });
    setupKakaoAddressSearch({
      postalCodeInput: form.elements.postalCode, basicAddressInput: form.elements.basicAddress,
      detailAddressInput: form.elements.detailAddress, searchButton: form.querySelector('[data-address-search]'),
      statusElement: form.querySelector('[data-address-search-status]'), shouldKeepDisabled: () => busy,
    });
    try {
      session = await currentSession(); await reload(); form.hidden = true;
      message.textContent = '배송지를 선택해 수정하거나 새 배송지를 등록해 주세요.';
    } catch (error) {
      if (error.accountChanged) loginMessage(message, error.message); else message.textContent = error.message;
    }
    window.addEventListener('focus', async () => {
      if (!session) return;
      try { await currentSession(session.checkoutAccount); }
      catch (error) { if (error.accountChanged) { clearPrivate(); loginMessage(message, error.message); } }
    });
  }

  function checkout({ form, session, hasSubmission, isSubmitting }) {
    const inputs = {
      recipientName: form.elements.recipientName, phoneNumber: form.elements.phoneNumber,
      postalCode: form.querySelector('[data-address-postal-code]'),
      basicAddress: form.querySelector('[data-address-basic]'), detailAddress: form.querySelector('[data-address-detail]'),
    };
    const initial = Object.fromEntries(Object.entries(inputs).map(([name, input]) => [name, input.value]));
    let touched = Object.entries(initial).some(([name, value]) => name === 'recipientName' ? value !== (session.name || '') : Boolean(value));
    let autoApplied = false, generation = 0, rows = [];
    const panel = document.createElement('div'); panel.className = 'saved-address-picker';
    const label = document.createElement('label'); label.textContent = '저장 배송지 선택 (선택하면 현재 배송 정보가 바뀝니다)';
    const select = document.createElement('select'); select.setAttribute('aria-label', '저장 배송지 선택');
    const message = document.createElement('p'); message.setAttribute('role', 'status');
    const retry = document.createElement('button'); retry.type = 'button'; retry.textContent = '배송지 다시 조회';
    const manageLink = document.createElement('a'); manageLink.href = 'shipping-addresses.html'; manageLink.textContent = '배송지 관리';
    label.append(select); panel.append(label, message, retry, manageLink);
    form.querySelector('.checkout-fields').before(panel);
    function resetOptions() {
      select.replaceChildren();
      const manual = document.createElement('option'); manual.value = ''; manual.textContent = '직접 입력 / 현재 입력 유지'; select.append(manual);
    }
    resetOptions();
    Object.values(inputs).forEach(input => {
      input.addEventListener('input', () => { touched = true; select.value = ''; });
      input.addEventListener('change', () => { touched = true; select.value = ''; });
    });
    function apply(row) {
      if (hasSubmission() || isSubmitting()) return;
      for (const [name, input] of Object.entries(inputs)) { input.value = row[name] || ''; input.setCustomValidity(''); }
      select.value = String(row.id);
    }
    select.addEventListener('change', () => {
      if (hasSubmission() || isSubmitting()) { message.textContent = '접수 중이거나 접수된 주문의 배송 정보는 바꾸지 않습니다.'; return; }
      touched = true;
      const row = rows.find(entry => String(entry.id) === select.value);
      if (row) { apply(row); message.textContent = '선택한 배송지를 주문서에 입력했습니다. 직접 수정해도 주소록은 바뀌지 않습니다.'; }
    });
    async function refresh() {
      if (hasSubmission() || isSubmitting()) return;
      const token = ++generation; retry.disabled = true;
      try {
        const book = await request(session.checkoutAccount);
        if (token !== generation || hasSubmission() || isSubmitting()) return;
        rows = book.items; resetOptions();
        for (const row of rows) {
          const option = document.createElement('option'); option.value = String(row.id);
          option.textContent = row.label + (row.defaultAddress ? ' · 기본' : ''); select.append(option);
        }
        const row = rows.find(entry => entry.defaultAddress);
        const unchanged = Object.entries(inputs).every(([name, input]) => input.value === initial[name]);
        if (row && !touched && !autoApplied && unchanged) {
          apply(row); autoApplied = true; message.textContent = '기본 배송지를 입력했습니다. 다른 배송지를 선택하거나 직접 수정할 수 있습니다.';
        } else message.textContent = !rows.length ? '등록한 배송지가 없습니다. 직접 입력해 주세요.'
          : !row ? '기본 배송지가 없습니다. 배송지를 선택하거나 직접 입력해 주세요.' : '현재 배송 정보는 유지했습니다. 다른 배송지를 선택할 수 있습니다.';
      } catch (error) {
        rows = []; resetOptions();
        if (error.accountChanged) {
          Object.values(inputs).forEach(input => { input.value = ''; });
          form.querySelector('[data-order-submit]').disabled = true;
          message.textContent = error.message;
        } else message.textContent = '배송지 조회에 실패했습니다. 직접 입력하거나 다시 조회해 주세요.';
      } finally { retry.disabled = false; }
    }
    retry.onclick = refresh;
    protectHistory(() => { rows = []; generation++; resetOptions(); Object.values(inputs).forEach(input => { input.value = ''; }); });
    window.addEventListener('focus', async () => {
      try { await currentSession(session.checkoutAccount); }
      catch (error) {
        if (error.accountChanged) {
          rows = []; generation++; resetOptions(); Object.values(inputs).forEach(input => { input.value = ''; });
          form.querySelector('[data-order-submit]').disabled = true; message.textContent = error.message;
        }
      }
    });
    refresh();
  }
  return { manage, checkout };
})();
