import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import StatusPage from './StatusPage'

describe('StatusPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('초기 개발 환경과 백엔드 상태 영역을 렌더링한다', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))

    render(<StatusPage />)

    expect(screen.getByRole('heading', { name: '수원 랜덤 여행 코스' })).toBeInTheDocument()
    expect(screen.getByText('초기 개발 환경')).toBeInTheDocument()
    expect(screen.getByText('백엔드 연결')).toBeInTheDocument()
    expect(screen.getByText('확인 중')).toBeInTheDocument()
  })

  it('Actuator가 UP이면 정상 연결 상태를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ status: 'UP' }),
      }),
    )

    render(<StatusPage />)

    expect(await screen.findByText('연결됨 (UP)')).toBeInTheDocument()
    expect(screen.getByText('Spring Boot와 PostgreSQL 연결이 정상입니다.')).toBeInTheDocument()
  })
})
