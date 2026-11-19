function policyPageLink(document) {
  return `policies.html?document=${encodeURIComponent(document.documentCode)}&version=${encodeURIComponent(document.version)}`;
}

async function fetchPolicyJson(path) {
  const response = await fetch(path, { credentials: "same-origin", cache: "no-store" }).catch(() => {
    throw new Error("서비스 정책을 불러오지 못했습니다. 연결 상태를 확인하고 다시 조회해 주세요.");
  });
  const body = await response.json().catch(() => {
    throw new Error("서비스 정책 응답을 확인하지 못했습니다. 다시 조회해 주세요.");
  });
  if (!response.ok) throw new Error(body?.error || "서비스 정책을 불러오지 못했습니다.");
  if (!body || !body.data) throw new Error("서비스 정책 응답을 확인하지 못했습니다. 다시 조회해 주세요.");
  return body.data;
}

function policyLink(document) {
  const link = window.document.createElement("a");
  link.href = policyPageLink(document);
  link.textContent = `${document.title} (${document.version})`;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  return link;
}

function createSignupPolicyState(form) {
  const container = form.querySelector('[data-signup-policies]');
  const message = form.querySelector('[data-policy-status]');
  const retry = form.querySelector('[data-policy-retry]');
  const submit = form.querySelector('.login-submit');
  const all = form.querySelector('[data-policy-all]');
  const email = form.querySelector('[name="emailReceiveConsent"]');
  let documents = [], ready = false, loading = false, busy = false, generation = 0;
  let inputs = new Map();
  function sync() {
    const values = [...inputs.values()];
    const checked = values.filter(input => input.checked).length;
    all.checked = values.length > 0 && checked === values.length;
    all.indeterminate = checked > 0 && checked < values.length;
    all.disabled = busy || !ready || loading;
    for (const input of values) input.disabled = busy || !ready || loading;
    const optional = documents.find(d => d.consentMode === 'OPTIONAL_EMAIL');
    email.value = optional && inputs.get(optional.documentCode)?.checked ? 'Y' : 'N';
  }
  all.addEventListener('change', () => {
    if (busy || !ready || loading) return;
    inputs.forEach(input => { input.checked = all.checked; });
    sync();
  });
  async function load() {
    if (loading || busy) return;
    const revision = ++generation;
    loading = true; ready = false; submit.disabled = true; retry.disabled = true;
    inputs = new Map(); documents = []; email.value = 'N';
    container.replaceChildren(); sync();
    message.textContent = '서비스 정책을 확인하고 있습니다.';
    try {
      const catalog = await fetchPolicyJson('/api/policies');
      documents = catalog.documents;
      const notices = document.createElement('div'); notices.className = 'signup-policy-notices';
      const displayOrder = { REQUIRED: 0, OPTIONAL_EMAIL: 1, NONE: 2 };
      for (const policy of [...documents].sort((a, b) => displayOrder[a.consentMode] - displayOrder[b.consentMode])) {
        if (policy.consentMode === 'NONE') { notices.append(policyLink(policy)); continue; }
        const item = document.createElement('div'); item.className = 'signup-policy-item';
        const row = document.createElement('div'); row.className = 'signup-policy-row';
        const label = document.createElement('label');
        const input = document.createElement('input'); input.type = 'checkbox';
        input.required = policy.consentMode === 'REQUIRED';
        const text = document.createElement('span');
        text.textContent = `${policy.title} (${input.required ? '필수' : '선택'})`;
        label.append(input, text); inputs.set(policy.documentCode, input);
        input.addEventListener('change', sync);
        const toggle = document.createElement('button'); toggle.type = 'button'; toggle.className = 'signup-policy-toggle';
        const panel = document.createElement('div'); panel.className = 'signup-policy-panel'; panel.hidden = true;
        panel.id = `signup-policy-panel-${inputs.size}`;
        toggle.setAttribute('aria-controls', panel.id); toggle.setAttribute('aria-expanded', 'false');
        toggle.setAttribute('aria-label', `${policy.title} 내용 펼치기`);
        toggle.textContent = '⌄';
        const status = document.createElement('p'); status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
        const content = document.createElement('div'); content.className = 'signup-policy-body';
        const again = document.createElement('button'); again.type = 'button'; again.textContent = '내용 다시 조회'; again.hidden = true;
        panel.append(status, content, again);
        let loaded = false, fetching = false;
        async function loadBody() {
          if (loaded || fetching) return;
          fetching = true; again.hidden = true; status.textContent = '내용을 불러오고 있습니다.';
          try {
            const body = await fetchPolicyJson(policy.bodyPath);
            if (revision !== generation) return;
            if (body.documentCode !== policy.documentCode || body.version !== policy.version || typeof body.body !== 'string') {
              throw new Error('정책 문서가 변경되었습니다. 정책을 다시 조회해 주세요.');
            }
            content.textContent = body.body; loaded = true; status.textContent = '';
          } catch (error) {
            if (revision !== generation) return;
            status.textContent = error.message || '내용을 불러오지 못했습니다. 다시 조회해 주세요.';
            again.hidden = false;
          } finally { fetching = false; }
        }
        toggle.addEventListener('click', () => {
          panel.hidden = !panel.hidden;
          toggle.setAttribute('aria-expanded', String(!panel.hidden));
          toggle.setAttribute('aria-label', `${policy.title} 내용 ${panel.hidden ? '펼치기' : '접기'}`);
          toggle.textContent = panel.hidden ? '⌄' : '⌃';
          if (!panel.hidden) loadBody();
        });
        again.addEventListener('click', loadBody);
        row.append(label, toggle); item.append(row, panel); container.append(item);
      }
      if (notices.children.length) container.append(notices);
      ready = catalog.signupReady === true;
      message.textContent = ready ? '' : '서비스 정책 승인 및 회원가입 준비 중입니다.';
    } catch (error) { message.textContent = error.message || '서비스 정책을 불러오지 못했습니다. 다시 조회해 주세요.'; }
    finally { loading = false; retry.disabled = false; submit.disabled = !ready; sync(); }
  }
  retry.addEventListener('click', load);
  return {
    load,
    setBusy(value) { busy = value; retry.disabled = busy; sync(); },
    ready: () => ready && !loading,
    consents() {
      if (!ready || loading) throw new Error('서비스 정책을 먼저 확인해 주세요.');
      return documents.filter(d => d.consentMode !== 'NONE').map(d => ({
        documentCode: d.documentCode, version: d.version, agreed: inputs.get(d.documentCode).checked,
      }));
    },
  };
}

async function initPolicyPage() {
  const page = document.querySelector("[data-policy-page]");
  if (!page) return;
  const status = page.querySelector("[data-policy-page-status]");
  const content = page.querySelector("[data-policy-content]");
  const retry = page.querySelector("[data-policy-page-retry]");
  let loading = false;
  async function load() {
    if (loading) return;
    loading = true; retry.disabled = true; content.replaceChildren();
    status.textContent = "서비스 정책을 불러오고 있습니다.";
    try {
      const query = new URLSearchParams(window.location.search);
      const code = query.get("document"), version = query.get("version");
      if (code) {
        const selectedVersion = version || (await fetchPolicyJson("/api/policies")).documents.find(d => d.documentCode === code)?.version;
        if (!selectedVersion) throw new Error("게시된 정책 문서를 찾을 수 없습니다.");
        const policy = await fetchPolicyJson(`/api/policies/${encodeURIComponent(code)}/${encodeURIComponent(selectedVersion)}`);
        const heading = document.createElement("h2"); heading.textContent = policy.title;
        const meta = document.createElement("p"); meta.textContent = `버전 ${policy.version} · 적용일 ${policy.effectiveAt}`;
        const body = document.createElement("div"); body.className = "policy-body"; body.textContent = policy.body;
        content.append(heading, meta, body);
        status.textContent = "가입 시 동의한 버전은 이후에도 이 주소에서 확인할 수 있습니다.";
      } else {
        const catalog = await fetchPolicyJson("/api/policies");
        for (const policy of catalog.documents) {
          const row = document.createElement("p");
          const link = policyLink(policy); link.removeAttribute("target"); row.append(link); content.append(row);
        }
        status.textContent = catalog.documents.length ? "게시된 서비스 정책입니다." : "서비스 정책 승인 및 게시 준비 중입니다.";
      }
    } catch (error) { status.textContent = error.message || "정책 조회에 실패했습니다. 다시 조회해 주세요."; }
    finally { loading = false; retry.disabled = false; }
  }
  retry.addEventListener("click", load);
  await load();
}
initPolicyPage();
