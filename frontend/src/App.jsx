import { useEffect, useState } from 'react'

const checkingStatus = {
  className: 'checking',
  label: '확인 중',
  message: '백엔드 상태를 확인하고 있습니다.',
}

function App() {
  const [backendStatus, setBackendStatus] = useState(checkingStatus)

  useEffect(() => {
    let isMounted = true

    async function checkBackend() {
      try {
        const response = await fetch('/api/health', {
          headers: { Accept: 'application/json' },
        })

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }

        const health = await response.json()
        if (isMounted) {
          const isUp = health.status === 'UP'
          setBackendStatus({
            className: isUp ? 'up' : 'down',
            label: isUp ? '연결됨 (UP)' : `응답 상태: ${health.status ?? '알 수 없음'}`,
            message: isUp
              ? 'Spring Boot와 PostgreSQL 연결이 정상입니다.'
              : '백엔드 또는 데이터베이스 상태를 확인해 주세요.',
          })
        }
      } catch {
        if (isMounted) {
          setBackendStatus({
            className: 'down',
            label: '연결 실패',
            message: '백엔드 컨테이너와 health check 상태를 확인해 주세요.',
          })
        }
      }
    }

    checkBackend()

    return () => {
      isMounted = false
    }
  }, [])

  return (
    <main className="page-shell">
      <section className="status-card" aria-labelledby="project-title">
        <p className="eyebrow">초기 개발 환경</p>
        <h1 id="project-title">수원 랜덤 여행 코스</h1>
        <p className="intro">
          팀 개발을 시작하기 위한 빈 프로젝트가 실행 중입니다. 여행 기능은 아직 구현하지 않았습니다.
        </p>

        <dl className="status-list">
          <div>
            <dt>프론트엔드</dt>
            <dd className="status-value up">실행 중</dd>
          </div>
          <div>
            <dt>백엔드 연결</dt>
            <dd className={`status-value ${backendStatus.className}`} aria-live="polite">
              <strong>{backendStatus.label}</strong>
              <span>{backendStatus.message}</span>
            </dd>
          </div>
        </dl>

        <p className="notice">POI, 추천, 지도 등 실제 여행 기능은 팀 협업을 통해 이후 추가할 예정입니다.</p>
      </section>
    </main>
  )
}

export default App
