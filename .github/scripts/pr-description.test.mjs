import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

import {
  CONFIG,
  END_MARKER,
  GitHubClient,
  MarkerError,
  START_MARKER,
  buildAiPayload,
  classifyFile,
  createFallbackSummary,
  generateSummary,
  isBinaryPath,
  isSensitivePath,
  mergeManagedSection,
  normalizeFile,
  parseAiSummary,
  redactSecrets,
  renderManagedSection,
  requestAiSummary,
  run,
  truncateUtf8,
  updatePullRequestBody,
} from './pr-description.mjs';

const fixtureUrl = new URL('./fixtures/feature-3-files.json', import.meta.url);

function jsonResponse(value, status = 200, headers = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json', ...headers },
  });
}

function aiResponse(summary = ['핵심 변경'], reviewPoints = ['회귀 테스트 확인']) {
  return {
    status: 'completed',
    output: [{
      type: 'message',
      content: [{
        type: 'output_text',
        text: JSON.stringify({ summary, review_points: reviewPoints }),
      }],
    }],
  };
}

test('feature/3 자원을 저장소 영역별로 분류한다', async () => {
  const fixture = JSON.parse(await readFile(fixtureUrl, 'utf8'));
  const files = fixture.map(normalizeFile);
  const categories = new Map(files.map((file) => [file.filename, file.category]));

  assert.equal(categories.get('ARCHITECTURE.md'), 'Docs');
  assert.equal(categories.get('src/main/java/com/nanacocoa/server/payment/controller/PaymentClientConfigController.java'), 'Backend/API');
  assert.equal(categories.get('src/main/resources/static/checkout.html'), 'Frontend/static');
  assert.equal(categories.get('src/test/java/com/nanacocoa/server/payment/controller/PaymentClientConfigControllerTest.java'), 'Tests');
  assert.equal(categories.get('harness/baselines/test-inventory.txt'), 'CI/build/config');
  assert.equal(classifyFile('src/main/resources/db/migration/V4__sample.sql'), 'DB/Flyway');
  assert.equal(classifyFile('misc/notes.txt'), '기타');
});

test('민감 경로와 바이너리를 AI 입력에서 제외하고 patch를 마스킹·제한한다', () => {
  assert.equal(isSensitivePath('.env.production'), true);
  assert.equal(isSensitivePath('config/client-secret.pem'), true);
  assert.equal(isSensitivePath('config/client-secret.json'), true);
  assert.equal(isBinaryPath('src/main/resources/static/photo.JPG'), true);

  const secretPatch = '+api_key=sk-abcdefghijklmnopqrstuvwxyz\n+password=hunter2';
  const files = [
    normalizeFile({ filename: '.env', status: 'modified', patch: '+SECRET=raw' }),
    normalizeFile({ filename: 'asset.png', status: 'added', patch: '+binary' }),
    normalizeFile({ filename: 'src/main/java/App.java', status: 'modified', patch: secretPatch }),
    normalizeFile({ filename: 'src/main/java/Large.java', status: 'modified', patch: `+${'가'.repeat(20_000)}` }),
  ];
  const payload = buildAiPayload(files, []);

  assert.deepEqual(payload.files.map((file) => file.filename), [
    'src/main/java/App.java',
    'src/main/java/Large.java',
  ]);
  assert.doesNotMatch(JSON.stringify(payload), /hunter2|sk-abcdefghijklmnopqrstuvwxyz|SECRET=raw|binary/);
  assert.match(payload.patches[0].patch, /\[REDACTED/);
  assert.ok(payload.patches.every((item) => Buffer.byteLength(item.patch) <= CONFIG.perFilePatchBytes));
  assert.ok(payload.patches.reduce((sum, item) => sum + Buffer.byteLength(item.patch), 0) <= CONFIG.totalPatchBytes);
});

test('UTF-8 문자열을 바이트 제한 안에서 자른다', () => {
  const result = truncateUtf8('가나다라마바사', 10);
  assert.ok(Buffer.byteLength(result, 'utf8') <= 10);
  assert.doesNotMatch(result, /�/);
});

test('GitHub pagination으로 모든 페이지를 수집한다', async () => {
  const pageOne = Array.from({ length: 100 }, (_, index) => ({ filename: `${index}.txt` }));
  const requested = [];
  const client = new GitHubClient({
    repository: 'owner/repo',
    token: 'token',
    fetchImpl: async (url) => {
      requested.push(url);
      return /[?&]page=1(?:&|$)/.test(url) ? jsonResponse(pageOne) : jsonResponse([{ filename: 'last.txt' }]);
    },
  });

  const files = await client.listPullRequestFiles(7);
  assert.equal(files.length, 101);
  assert.equal(requested.length, 2);
  assert.match(requested[1], /page=2/);
});

test('작성자 본문을 보존하며 관리 구역을 추가하거나 교체한다', () => {
  const section = `${START_MARKER}\n새 내용\n${END_MARKER}`;
  assert.equal(mergeManagedSection('', section), section);
  assert.equal(mergeManagedSection('작성자 본문', section), `작성자 본문\n\n${section}`);

  const existing = `앞\n${START_MARKER}\n이전\n${END_MARKER}\n뒤`;
  assert.equal(mergeManagedSection(existing, section), `앞\n${section}\n뒤`);
});

test('누락·중복·역순 관리 마커에서는 본문을 변경하지 않는다', () => {
  const section = `${START_MARKER}\n새 내용\n${END_MARKER}`;
  assert.throws(() => mergeManagedSection(`본문\n${START_MARKER}`, section), MarkerError);
  assert.throws(() => mergeManagedSection(`${START_MARKER}${START_MARKER}${END_MARKER}`, section), MarkerError);
  assert.throws(() => mergeManagedSection(`${END_MARKER}${START_MARKER}`, section), MarkerError);
});

test('구조화 출력을 검증하고 위험한 Markdown과 마커를 제거한다', () => {
  const parsed = parseAiSummary(aiResponse(
    [`변경 <!-- hidden --> ${START_MARKER} ![추적](https://example.com/a.png)`],
    ['<b>보안</b>\n검토'],
  ));
  assert.equal(parsed.summary[0], '변경');
  assert.equal(parsed.reviewPoints[0], '보안 검토');
  assert.throws(() => parseAiSummary({ output_text: '{bad json' }), SyntaxError);
  assert.throws(() => parseAiSummary({ output_text: '{"summary":[],"review_points":[]}' }));
});

test('OpenAI 429를 재시도하고 Responses API 계약을 보낸다', async () => {
  const requests = [];
  const sleeps = [];
  const result = await requestAiSummary(
    { files: [], commits: [], patches: [] },
    {
      apiKey: 'test-key',
      fetchImpl: async (_url, options) => {
        requests.push(JSON.parse(options.body));
        return requests.length === 1
          ? jsonResponse({ error: 'rate limited' }, 429, { 'retry-after': '0' })
          : jsonResponse(aiResponse());
      },
      sleepImpl: async (ms) => sleeps.push(ms),
      timeoutMs: 100,
    },
  );

  assert.deepEqual(result.summary, ['핵심 변경']);
  assert.equal(requests.length, 2);
  assert.equal(sleeps.length, 1);
  assert.equal(requests[0].model, 'gpt-5.6-luna');
  assert.equal(requests[0].store, false);
  assert.deepEqual(requests[0].reasoning, { effort: 'low' });
  assert.equal(requests[0].text.format.type, 'json_schema');
  assert.equal(requests[0].text.format.strict, true);
});

test('OpenAI timeout과 잘못된 출력은 오류로 반환한다', async () => {
  await assert.rejects(
    requestAiSummary({}, {
      apiKey: 'test-key',
      timeoutMs: 5,
      sleepImpl: async () => {},
      fetchImpl: async (_url, { signal }) => new Promise((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
      }),
    }),
    /aborted/,
  );

  await assert.rejects(
    requestAiSummary({}, {
      apiKey: 'test-key',
      fetchImpl: async () => jsonResponse({ output_text: '{bad json' }),
    }),
    SyntaxError,
  );
});

test('API 키 누락과 AI 실패에서는 결정론적 폴백을 사용한다', async () => {
  const files = [normalizeFile({ filename: 'src/main/java/App.java', additions: 2, deletions: 1 })];
  const commits = [{ sha: 'abc', title: 'feat: sample' }];

  const missingKey = await generateSummary(files, commits, { apiKey: '' });
  assert.equal(missingKey.aiUsed, false);
  assert.match(missingKey.warning, /OPENAI_API_KEY/);
  assert.match(missingKey.generated.summary[0], /1개 파일/);

  const failed = await generateSummary(files, commits, {
    apiKey: 'test-key',
    requestImpl: async () => { throw new Error('service unavailable'); },
  });
  assert.equal(failed.aiUsed, false);
  assert.match(failed.warning, /service unavailable/);
});

test('본문 갱신 직전에 읽은 최신 수동 편집을 보존한다', async () => {
  let patchedBody;
  const client = {
    getPullRequest: async () => ({ body: '작성자가 실행 중에 추가한 설명' }),
    updatePullRequest: async (_number, body) => { patchedBody = body; },
  };
  const section = `${START_MARKER}\n자동 내용\n${END_MARKER}`;

  const result = await updatePullRequestBody(client, 3, section);
  assert.equal(result.changed, true);
  assert.match(patchedBody, /^작성자가 실행 중에 추가한 설명/);
  assert.match(patchedBody, /자동 내용/);
});

test('관리 구역에 요약, 자원, 통계를 고정 형식으로 렌더링한다', () => {
  const files = [
    normalizeFile({ filename: 'src/main/java/App.java', status: 'added', additions: 4 }),
    normalizeFile({ filename: 'src/test/java/AppTest.java', status: 'modified', additions: 2, deletions: 1 }),
  ];
  const generated = createFallbackSummary(files, [{ sha: 'abc', title: 'test' }]);
  const section = renderManagedSection({
    files,
    commits: [{ sha: 'abc', title: 'test' }],
    generated,
    aiUsed: false,
    generatedAt: new Date('2026-08-22T00:00:00.000Z'),
  });

  assert.match(section, /^<!-- pr-auto:start -->/);
  assert.match(section, /#### Backend\/API/);
  assert.match(section, /#### Tests/);
  assert.match(section, /파일 2개 · 커밋 1개 · \+6 \/ -1/);
  assert.match(section, /결정론적 폴백 요약/);
  assert.match(section, /2026-08-22T00:00:00.000Z/);
  assert.match(section, /<!-- pr-auto:end -->$/);
});

test('비정상 GitHub 응답과 API 오류를 실패로 처리한다', async () => {
  const client = new GitHubClient({
    repository: 'owner/repo',
    token: 'token',
    fetchImpl: async () => jsonResponse({ message: 'forbidden' }, 403),
  });
  await assert.rejects(client.listPullRequestFiles(1), /GitHub API/);

  const nonArrayClient = new GitHubClient({
    repository: 'owner/repo',
    token: 'token',
    fetchImpl: async () => jsonResponse({}),
  });
  await assert.rejects(nonArrayClient.listPullRequestFiles(1), /배열이 아닙니다/);
});

test('API 키가 없는 전체 실행은 최신 PR 본문에 폴백 구역을 기록한다', async () => {
  const originalFetch = globalThis.fetch;
  let patchedBody = null;
  globalThis.fetch = async (url, options = {}) => {
    if (url.includes('/pulls/11/files')) {
      return jsonResponse([{ filename: 'README.md', status: 'modified', additions: 2, deletions: 1, patch: '+설명' }]);
    }
    if (url.includes('/pulls/11/commits')) {
      return jsonResponse([{ sha: 'abcdef1234567890', commit: { message: 'docs: update guide\n\nbody' } }]);
    }
    if (url.endsWith('/pulls/11') && (options.method ?? 'GET') === 'GET') {
      return jsonResponse({ body: '작성자 설명' });
    }
    if (url.endsWith('/pulls/11') && options.method === 'PATCH') {
      patchedBody = JSON.parse(options.body).body;
      return jsonResponse({ body: patchedBody });
    }
    throw new Error(`예상하지 못한 URL: ${url}`);
  };

  try {
    const result = await run({
      PR_NUMBER: '11',
      GITHUB_REPOSITORY: 'owner/repo',
      GITHUB_TOKEN: 'github-token',
      OPENAI_API_KEY: '',
    });
    assert.equal(result.aiUsed, false);
    assert.match(result.warning, /OPENAI_API_KEY/);
    assert.match(patchedBody, /^작성자 설명/);
    assert.match(patchedBody, /결정론적 폴백 요약/);
    assert.match(patchedBody, /`README.md`/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('redaction은 대표 자격 증명 형태를 제거한다', () => {
  const input = [
    'Authorization: Bearer super-secret-token',
    'access_token=plain-token',
    'password="quoted secret with spaces"',
    'AKIA1234567890ABCDEF',
    'eyJabcdefgh.ijklmnop.qrstuvwx',
  ].join('\n');
  const redacted = redactSecrets(input);
  assert.doesNotMatch(redacted, /super-secret-token|plain-token|quoted secret|AKIA1234567890ABCDEF|eyJabcdefgh/);
});
