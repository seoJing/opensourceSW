import { execFileSync } from 'node:child_process'
import { readFileSync, existsSync, lstatSync } from 'node:fs'
import { basename, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const policy = JSON.parse(readFileSync(new URL('../.github/harness-policy.json', import.meta.url), 'utf8'))

export function sharedFiles(paths) {
  return [...new Set(paths)].filter(path =>
    policy.sharedFiles.includes(path) ||
    policy.sharedPrefixes.some(prefix => path.startsWith(prefix)) ||
    policy.sharedBasenames.includes(basename(path)),
  ).sort()
}

export function pathProblem(path) {
  const parts = path.split('/')
  const name = basename(path)
  if ((name === '.env' || name.startsWith('.env.')) && name !== '.env.example') {
    return '실제 환경 파일은 커밋할 수 없습니다.'
  }
  if (parts.some(part => ['node_modules', 'target', 'dist', 'coverage', '.vite', '.vitest', '__pycache__'].includes(part)) ||
      /\.(log|pyc)$/.test(name)) {
    return '생성 파일은 커밋할 수 없습니다.'
  }
  return null
}

export function conflictLines(content) {
  return content.split(/\r?\n/).flatMap((line, index) =>
    /^(<{7}(?: |$)|={7}$|>{7}(?: |$)|\|{7}(?: |$))/.test(line) ? [index + 1] : [],
  )
}

export function prProblems(paths, body = '', presentPaths = paths) {
  // HTML 주석 속 예시와 체크박스는 합의 표시로 인정하지 않습니다.
  const visibleBody = body.replace(/<!--[\s\S]*?-->/g, '')
  const errors = presentPaths.flatMap(path => pathProblem(path) ? [`${path}: ${pathProblem(path)}`] : [])
  if (sharedFiles(paths).length) {
    if (!visibleBody.split(/\r?\n/).some(line =>
      /^\s*- \[[xX]\] /.test(line) && line.trim().endsWith(policy.approvalText))) {
      errors.push(`공통 파일 변경: PR 본문에 '- [x] ${policy.approvalText}'를 표시하세요.`)
    }
    if (!/(?:^|\s)#\d+\b|https:\/\/github\.com\/[^\s/]+\/[^\s/]+\/issues\/\d+\b/.test(visibleBody)) {
      errors.push('공통 파일 변경의 필요성과 영향을 합의한 Issue를 PR 본문에 연결하세요.')
    }
  }
  return errors
}

export function overlaps(ours, theirs) {
  const paths = new Set(ours)
  return [...new Set(theirs)].filter(path => paths.has(path)).sort()
}

export function changedPaths(files) {
  // rename 이전 경로도 포함해야 공통 파일을 이동해 감지를 피할 수 없습니다.
  return [...new Set(files.flatMap(file => [file.filename, file.previous_filename].filter(Boolean)))]
}

function git(...args) {
  return execFileSync('git', args, { encoding: 'utf8' })
}

function checkRepository() {
  const paths = git('ls-files', '--cached', '--others', '--exclude-standard', '-z').split('\0').filter(Boolean)
  const errors = []
  for (const path of new Set(paths)) {
    const problem = pathProblem(path)
    if (problem) errors.push(`${path}: ${problem}`)
    if (!existsSync(path) || !lstatSync(path).isFile()) continue
    const content = readFileSync(path)
    if (content.includes(0)) continue
    for (const line of conflictLines(content.toString('utf8'))) {
      errors.push(`${path}:${line}: 해결되지 않은 병합 충돌 표시가 있습니다.`)
    }
  }
  const base = process.argv[2]
  if (base) {
    const changes = git('diff', '--name-only', '--no-renames', '-z', `${base}...HEAD`).split('\0').filter(Boolean)
    console.log('공통 설정 변경:', sharedFiles(changes).join(', ') || '없음')
  }
  if (errors.length) {
    console.error(errors.join('\n'))
    process.exitCode = 1
  } else {
    console.log('Repository Guard: 충돌 표시 및 금지 파일 검사 통과')
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  checkRepository()
}
