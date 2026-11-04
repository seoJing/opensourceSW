import { appendFileSync } from 'node:fs'
import { changedPaths, overlaps, prProblems, sharedFiles } from './repo-harness.mjs'

// pull_request_target에서 기본 브랜치의 코드만 실행합니다. PR 코드를 checkout하지 않습니다.
export default async function coordinate({ github, context, core }) {
  const { owner, repo } = context.repo
  const number = context.payload.pull_request.number
  const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: number })
  const files = await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: number, per_page: 100 })
  // GitHub 파일 목록 API는 최대 3,000개입니다. 부분 목록으로 성공 처리하지 않습니다.
  if (files.length !== pr.changed_files) {
    throw new Error('PR 변경 파일 전체를 확인할 수 없습니다. PR 크기를 줄여 주세요.')
  }
  const paths = changedPaths(files)
  const lines = ['# PR 협업 점검', '', `PR #${number}`, '', '## 공통 설정 변경', '']
  lines.push(...sharedFiles(paths).map(path => `- ${JSON.stringify(path)}`))
  if (!sharedFiles(paths).length) lines.push('없음')
  const presentPaths = files.filter(file => file.status !== 'removed').map(file => file.filename)
  for (const problem of prProblems(paths, pr.body ?? '', presentPaths)) core.setFailed(problem)

  if (pr.mergeable === false) core.setFailed('main과 병합 충돌이 있습니다. 최신 main을 반영하고 해결하세요.')
  if (pr.mergeable === null) core.warning('GitHub가 병합 가능 여부를 계산 중입니다. 병합 전 PR 화면에서 재확인하세요.')
  lines.push('', '## 다른 열린 PR과 겹치는 파일', '')
  const others = await github.paginate(github.rest.pulls.list, {
    owner, repo, state: 'open', base: pr.base.ref, per_page: 100,
  })
  let overlapCount = 0
  for (const other of others.filter(item => item.number !== number)) {
    const otherFiles = await github.paginate(github.rest.pulls.listFiles, {
      owner, repo, pull_number: other.number, per_page: 100,
    })
    if (otherFiles.length === 3000) core.warning(`PR #${other.number}는 API 파일 개수 제한에 도달했습니다. 중복 파일 수동 확인이 필요합니다.`)
    const common = overlaps(paths, changedPaths(otherFiles))
    if (!common.length) continue
    overlapCount += 1
    core.warning(`PR #${other.number}와 ${common.length}개 파일이 겹칩니다. 담당자끼리 병합 순서를 합의하세요.`)
    lines.push(`- PR #${other.number}: ${common.map(path => JSON.stringify(path)).join(', ')}`)
  }
  if (!overlapCount) lines.push('겹치는 파일 없음')
  lines.push('', '파일 중복은 충돌 가능성 안내이며 실제 충돌과 같지 않습니다. 합의 표시는 리뷰 승인을 대신하지 않습니다.', '')
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, lines.join('\n'))
}
