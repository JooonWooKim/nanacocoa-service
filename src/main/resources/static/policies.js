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
  const container = form.querySelector("[data-signup-policies]");
  const message = form.querySelector("[data-policy-status]");
  const retry = form.querySelector("[data-policy-retry]");
  const submit = form.querySelector(".login-submit");
  let documents = [], ready = false, loading = false, busy = false, inputs = new Map();
  async function load() {
    if (loading || busy) return;
    loading = true; ready = false; submit.disabled = true; retry.disabled = true;
    inputs = new Map(); container.replaceChildren();
    // 다시 받은 문서에 이전 버전의 선택 동의를 자동 적용하지 않는다.
    const refusal = form.querySelector('[name="emailReceiveConsent"][value="N"]');
    if (refusal) refusal.checked = true;
    message.textContent = "서비스 정책을 확인하고 있습니다.";
    try {
      const catalog = await fetchPolicyJson("/api/policies");
      documents = catalog.documents;
      for (const document of documents) {
        const row = window.document.createElement("div");
        if (document.consentMode === "REQUIRED") {
          const label = window.document.createElement("label");
          const input = window.document.createElement("input");
          input.type = "checkbox"; input.required = true;
          const text = window.document.createElement("span"); text.textContent = "[필수] ";
          label.append(input, text, policyLink(document)); row.append(label);
          inputs.set(document.documentCode, input);
        } else {
          const text = window.document.createElement("span");
          text.textContent = document.consentMode === "OPTIONAL_EMAIL" ? "[선택 이메일 수신] " : "[안내] ";
          row.append(text, policyLink(document));
        }
        container.append(row);
      }
      ready = catalog.signupReady === true;
      message.textContent = ready ? "필수 항목과 선택 이메일 수신 안내를 확인해 주세요." : "서비스 정책 승인 및 회원가입 준비 중입니다.";
    } catch (error) {
      message.textContent = error.message || "서비스 정책을 불러오지 못했습니다. 다시 조회해 주세요.";
    } finally {
      loading = false; retry.disabled = false; submit.disabled = !ready;
    }
  }
  retry.addEventListener("click", load);
  return {
    load,
    setBusy(value) { busy = value; retry.disabled = busy; },
    ready: () => ready && !loading,
    consents(email) {
      if (!ready || loading) throw new Error("서비스 정책을 먼저 확인해 주세요.");
      return documents.filter(d => d.consentMode !== "NONE").map(d => ({
        documentCode: d.documentCode, version: d.version,
        agreed: d.consentMode === "OPTIONAL_EMAIL" ? email === "Y" : inputs.get(d.documentCode).checked,
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
