import { test } from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import coordinate from './pr-coordination.mjs'
import { changedPaths, conflictLines, overlaps, pathProblem, policy, prProblems, sharedFiles } from './repo-harness.mjs'

test('공통 구조 및 실행 설정은 감지하고 일반 기능 파일은 허용한다', () => {
  assert.deepEqual(sharedFiles([
    'frontend/src/features/example.jsx', 'backend/src/main/java/com/example/Example.java',
    'frontend/pnpm-lock.yaml', 'frontend/nginx.conf', 'backend/pom.xml',
    'docker-compose.yml', '.github/workflows/test.yml', 'frontend/AGENTS.md',
  ]), ['.github/workflows/test.yml', 'backend/pom.xml', 'docker-compose.yml',
    'frontend/AGENTS.md', 'frontend/nginx.conf', 'frontend/pnpm-lock.yaml'])
})

test('rename 이전 공통 경로와 삭제 파일도 검사한다', () => {
  const paths = changedPaths([
    { filename: 'archive/pom.xml', previous_filename: 'backend/pom.xml', status: 'renamed' },
    { filename: 'frontend/nginx.conf', status: 'removed' },
  ])
  assert.deepEqual(sharedFiles(paths), ['backend/pom.xml', 'frontend/nginx.conf'])
  assert.equal(prProblems(paths, '').length, 2)
})

test('공통 파일 변경에는 합의 체크와 Issue 연결이 모두 필요하다', () => {
  const paths = ['docker-compose.yml']
  const approval = `- [x] ${policy.approvalText}`
  assert.equal(prProblems(paths, '').length, 2)
  assert.equal(prProblems(paths, `Closes #5\n- [ ] ${policy.approvalText}`).length, 1)
  assert.equal(prProblems(paths, approval).length, 1)
  assert.equal(prProblems(paths, `Closes #5\n${approval}`).length, 0)
  assert.equal(prProblems(paths, `<!-- #5\n${approval} -->`).length, 2)
  assert.equal(prProblems(['frontend/src/Feature.jsx'], '').length, 0)
})

test('환경 파일과 생성 파일은 실패하되 예시와 재현 가능한 설치 파일은 허용한다', () => {
  for (const path of ['.env', 'backend/.env.local', 'frontend/dist/index.html', 'backend/target/app.jar', 'run.log']) {
    assert.ok(pathProblem(path), path)
  }
  for (const path of ['.env.example', 'frontend/pnpm-lock.yaml', 'backend/.mvn/wrapper/maven-wrapper.properties']) {
    assert.equal(pathProblem(path), null, path)
  }
  assert.deepEqual(prProblems(['.env', 'frontend/dist/index.html'], '', []), [], '금지 파일 삭제는 허용')
})

test('일반 텍스트와 구분해 merge 및 diff3 충돌 표시와 CRLF를 감지한다', () => {
  assert.deepEqual(conflictLines('<<<<<<< HEAD\r\nx\r\n||||||| base\r\ny\r\n=======\r\nz\r\n>>>>>>> branch'), [1, 3, 5, 7])
  assert.deepEqual(conflictLines('=== 제목 ===\nconst comparison = a < b\n'), [])
})

test('열린 PR과 실제로 겹치는 경로만 중복 없이 반환한다', () => {
  assert.deepEqual(overlaps(['a', 'b'], ['b', 'b', 'c']), ['b'])
  assert.deepEqual(overlaps(['a'], ['b']), [])
})

test('CLI가 실제 저장소에서 미해결 충돌과 강제 추적된 환경 파일을 실패 처리한다', () => {
  const directory = mkdtempSync(join(tmpdir(), 'suwon-harness-'))
  const command = fileURLToPath(new URL('./repo-harness.mjs', import.meta.url))
  const run = () => spawnSync(process.execPath, [command], { cwd: directory, encoding: 'utf8' })
  try {
    execFileSync('git', ['init', '--quiet'], { cwd: directory })
    writeFileSync(join(directory, '.gitignore'), '.env\n')
    writeFileSync(join(directory, '.env'), 'LOCAL_EXAMPLE=1\n')
    assert.equal(run().status, 0, '무시된 로컬 환경 파일은 검사 대상이 아님')
    execFileSync('git', ['add', '--force', '.env'], { cwd: directory })
    assert.equal(run().status, 1, '강제 추적된 환경 파일은 차단')
    execFileSync('git', ['rm', '--cached', '--force', '.env'], { cwd: directory })
    writeFileSync(join(directory, 'conflict.txt'), ['<<<<<<< HEAD', 'ours', '=======', 'theirs', '>>>>>>> other'].join('\n'))
    assert.match(run().stderr, /conflict\.txt:1/)
    writeFileSync(join(directory, 'conflict.txt'), 'resolved\n')
    assert.equal(run().status, 0)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
})

async function coordinationScenario({ files, body = '', mergeable = true, changedCount = files.length, otherFiles = [] }) {
  const directory = mkdtempSync(join(tmpdir(), 'suwon-coordination-'))
  const previousSummary = process.env.GITHUB_STEP_SUMMARY
  process.env.GITHUB_STEP_SUMMARY = join(directory, 'summary.md')
  const failures = []
  const warnings = []
  const pr = { number: 5, body, mergeable, changed_files: changedCount, base: { ref: 'main' } }
  const github = {
    rest: { pulls: {
      get: async () => ({ data: pr }),
      listFiles: 'files',
      list: 'pulls',
    } },
    paginate: async (method, options) => method === 'pulls'
      ? [{ number: 5 }, { number: 6 }]
      : options.pull_number === 5 ? files : otherFiles,
  }
  try {
    await coordinate({
      github,
      context: { repo: { owner: 'team', repo: 'example' }, payload: { pull_request: { number: 5 } } },
      core: { setFailed: text => failures.push(text), warning: text => warnings.push(text) },
    })
    return { failures, warnings, summary: readFileSync(process.env.GITHUB_STEP_SUMMARY, 'utf8') }
  } finally {
    if (previousSummary === undefined) delete process.env.GITHUB_STEP_SUMMARY
    else process.env.GITHUB_STEP_SUMMARY = previousSummary
    rmSync(directory, { recursive: true, force: true })
  }
}

test('PR 협업 검사가 공통 변경과 실제 충돌은 실패시키고 파일 중복은 경고로 보고한다', async () => {
  const result = await coordinationScenario({
    files: [{ filename: 'backend/pom.xml' }],
    mergeable: false,
    otherFiles: [{ filename: 'backend/pom.xml' }],
  })
  assert.equal(result.failures.length, 3)
  assert.equal(result.warnings.length, 1)
  assert.match(result.summary, /PR #6/)
  assert.match(result.summary, /backend\/pom\.xml/)
})

test('합의된 PR은 통과하고 병합 계산 중에는 명시적으로 재확인을 알린다', async () => {
  const result = await coordinationScenario({
    files: [{ filename: 'docker-compose.yml' }],
    body: `Closes #5\n- [x] ${policy.approvalText}`,
    mergeable: null,
  })
  assert.deepEqual(result.failures, [])
  assert.equal(result.warnings.length, 1)
  assert.match(result.summary, /겹치는 파일 없음/)
})

test('API에서 PR 파일 목록이 잘리면 성공 처리하지 않는다', async () => {
  await assert.rejects(coordinationScenario({ files: [], changedCount: 3001 }), /전체를 확인할 수 없습니다/)
})
