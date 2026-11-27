import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type Recommendation } from '../api/demoApi'
import { createMockApi, questionnaire } from '../mock/mockApi'
import { submission } from '../model/preferences'
import DemoPage from './DemoPage'
import { MapExperience } from './MapExperience'

vi.mock('./DemoMap', () => ({ DemoMap: () => <div aria-label="행궁동 카페 지도" /> }))

async function login(user = '테스트 사용자') {
  await waitFor(() => expect(screen.queryByText('서버 응답을 기다리고 있어요…')).not.toBeInTheDocument())
  fireEvent.click(await screen.findByRole('button', { name: '시작하기' }))
  fireEvent.change(screen.getByLabelText('아이디'), { target: { value: user } })
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'test-password' } })
  fireEvent.click(screen.getByRole('button', { name: '로그인' }))
}

async function finishSurvey() {
  fireEvent.click(await screen.findByRole('button', { name: '설문 시작' }))
  for (let step = 0; step < 4; step++) fireEvent.click(screen.getByRole('button', { name: '다음' }))
  fireEvent.click(screen.getByRole('button', { name: '완료' }))
}

describe('API-connected cafe journey', () => {
  beforeEach(() => sessionStorage.clear())

  it('submits dynamic answers, recommends after saving, navigates history and restores the session', async () => {
    const api = createMockApi()
    const save = vi.spyOn(api, 'savePreferences')
    const recommendations = vi.spyOn(api, 'recommend')
    const view = render(<DemoPage api={api} mock />)
    await login()
    fireEvent.click(await screen.findByRole('button', { name: '설문 시작' }))
    const next = () => fireEvent.click(screen.getByRole('button', { name: '다음' }))
    fireEvent.click(screen.getByRole('radio', { name: '음료' }))
    next(); next(); next()
    expect(screen.getByRole('heading', { name: questionnaire.questions[3].label })).toBeVisible()
    fireEvent.click(screen.getByRole('radio', { name: '커피 기반 음료' }))
    for (let step = 0; step < 3; step++) fireEvent.click(screen.getByRole('button', { name: '이전 단계' }))
    fireEvent.click(screen.getByRole('button', { name: '선택 해제' }))
    fireEvent.click(screen.getByRole('radio', { name: '상관없음' }))
    next(); next(); next()
    expect(screen.getByRole('heading', { name: questionnaire.questions[4].label })).toBeVisible()
    next()
    fireEvent.click(screen.getByRole('button', { name: '완료' }))
    const first = await screen.findByRole('button', { name: /자세히 보기/ })
    const firstName = first.getAttribute('aria-label')
    expect(save).toHaveBeenCalledWith(submission(questionnaire, { Q1: ['D'] }))
    expect(save.mock.invocationCallOrder[0]).toBeLessThan(recommendations.mock.invocationCallOrder[0])
    expect(recommendations).toHaveBeenNthCalledWith(1, [])
    fireEvent.click(screen.getByRole('button', { name: '다른 메뉴 보기' }))
    await waitFor(() => expect(screen.getByRole('button', { name: /자세히 보기/ }).getAttribute('aria-label')).not.toBe(firstName))
    expect(recommendations).toHaveBeenNthCalledWith(2, ['menu-01'])
    fireEvent.click(screen.getByRole('button', { name: '이전 메뉴' }))
    expect(screen.getByRole('button', { name: /자세히 보기/ })).toHaveAttribute('aria-label', firstName)
    view.unmount()

    render(<DemoPage api={createMockApi()} mock />)
    expect(await screen.findByRole('button', { name: /자세히 보기/ })).toBeVisible()
    fireEvent.click(screen.getByLabelText('마이페이지'))
    expect(screen.getByText('테스트 사용자님')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    expect(await screen.findByRole('button', { name: '시작하기' })).toBeVisible()
    expect(screen.queryByRole('button', { name: /자세히 보기/ })).not.toBeInTheDocument()
  })

  it('prompts for a new survey when a saved version differs', async () => {
    const api = createMockApi()
    vi.spyOn(api, 'getPreferences').mockResolvedValue({ saved: true, questionnaireVersion: 'old', answers: [], updatedAt: null })
    const recommend = vi.spyOn(api, 'recommend')
    render(<DemoPage api={api} />)
    await login()
    expect(await screen.findByRole('alert')).toHaveTextContent('설문이 변경됐어요')
    expect(recommend).not.toHaveBeenCalled()
  })

  it('refreshes the definition after a save version conflict', async () => {
    const api = createMockApi()
    const definition = vi.spyOn(api, 'getQuestionnaire')
    definition.mockResolvedValueOnce(questionnaire).mockResolvedValueOnce({
      ...questionnaire,
      version: 'v0.2.0',
      questions: [{ ...questionnaire.questions[0], label: '새 문항' }],
    })
    vi.spyOn(api, 'savePreferences').mockRejectedValueOnce(new ApiError(409, 'QUESTIONNAIRE_VERSION_MISMATCH', '새 설문을 작성해 주세요.'))
    const recommend = vi.spyOn(api, 'recommend')
    render(<DemoPage api={api} />)
    await login()
    await finishSurvey()
    expect(await screen.findByRole('alert')).toHaveTextContent('새 설문을 작성해 주세요')
    fireEvent.click(await screen.findByRole('button', { name: '설문 시작' }))
    expect(screen.getByRole('heading', { name: '새 문항' })).toBeVisible()
    expect(recommend).not.toHaveBeenCalled()
  })

  it('refreshes the session after a CSRF error and retries only the recommendation after a 500', async () => {
    const api = createMockApi()
    const save = vi.spyOn(api, 'savePreferences')
    const session = vi.spyOn(api, 'getSession')
    const recommend = vi.spyOn(api, 'recommend')
      .mockRejectedValueOnce(new ApiError(403, 'CSRF_INVALID', 'CSRF'))
      .mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', '추천 오류'))
    render(<DemoPage api={api} />)
    await login()
    await finishSurvey()
    const retry = await screen.findByRole('button', { name: '추천 다시 요청' })
    await waitFor(() => expect(retry).toBeEnabled())
    expect(session).toHaveBeenCalledTimes(2)
    expect(recommend).toHaveBeenCalledTimes(1)
    fireEvent.click(retry)
    expect(await screen.findByText('추천 오류')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '추천 다시 요청' }))
    expect(await screen.findByRole('button', { name: /자세히 보기/ })).toBeVisible()
    expect(save).toHaveBeenCalledOnce()
    expect(recommend.mock.calls).toEqual([[[]], [[]], [[]]])
  })

  it('clears stale answers and refreshes the definition when recommendations require preferences', async () => {
    const api = createMockApi()
    const definition = vi.spyOn(api, 'getQuestionnaire')
    const recommend = vi.spyOn(api, 'recommend')
    render(<DemoPage api={api} />)
    await login()
    await finishSurvey()
    await screen.findByRole('button', { name: /자세히 보기/ })
    recommend.mockRejectedValueOnce(new ApiError(409, 'PREFERENCES_REQUIRED', '현재 설문에 응답해 주세요.'))
    fireEvent.click(screen.getByRole('button', { name: '다른 메뉴 보기' }))
    await waitFor(() => expect(screen.getByRole('button', { name: '설문 시작' })).toBeEnabled())
    expect(definition).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('button', { name: /자세히 보기/ })).not.toBeInTheDocument()
    await finishSurvey()
    expect(await screen.findByRole('button', { name: /자세히 보기/ })).toBeVisible()
    expect(recommend).toHaveBeenNthCalledWith(3, [])
  })

  it('serializes rapid clicks, discards duplicate cards and clears data on expiration and account change', async () => {
    const api = createMockApi()
    const recommend = vi.spyOn(api, 'recommend')
    render(<DemoPage api={api} />)
    await login()
    await finishSurvey()
    const card = await screen.findByRole('button', { name: /자세히 보기/ })
    const first = await recommend.mock.results[0].value
    let resolveRecommendation!: (value: Recommendation) => void
    const request = new Promise<Recommendation>(resolve => { resolveRecommendation = resolve })
    recommend.mockReturnValueOnce(request)
    const next = screen.getByRole('button', { name: '다른 메뉴 보기' })
    fireEvent.click(next)
    fireEvent.click(next)
    fireEvent.click(screen.getByLabelText('마이페이지'))
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeDisabled()
    await waitFor(() => expect(recommend).toHaveBeenCalledTimes(2))
    resolveRecommendation(first)
    expect(await screen.findByRole('alert')).toBeVisible()
    expect(card).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이전 메뉴' })).toBeDisabled()

    recommend.mockRejectedValueOnce(new ApiError(401, 'AUTHENTICATION_REQUIRED', '로그인이 필요해요.'))
    fireEvent.click(next)
    const submit = await screen.findByRole('button', { name: '로그인' })
    expect(card).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('아이디'), { target: { value: '다른 사용자' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'test-password' } })
    fireEvent.click(submit)
    expect(await screen.findByRole('button', { name: '설문 시작' })).toBeVisible()
    expect(recommend.mock.calls.slice(1)).toEqual([[['menu-01']], [['menu-01']]])
  })

  it('keeps the draft and returns to the invalid question after a 422', async () => {
    const api = createMockApi()
    vi.spyOn(api, 'savePreferences').mockRejectedValueOnce(new ApiError(422, 'INVALID_ANSWERS', '설문을 확인해 주세요.', [
      { field: 'answers[0].optionIds', questionId: 'Q1', reason: 'SELECTION_COUNT', message: '메뉴 종류를 확인해 주세요.' },
    ]))
    const recommend = vi.spyOn(api, 'recommend')
    render(<DemoPage api={api} />)
    await login()
    fireEvent.click(await screen.findByRole('button', { name: '설문 시작' }))
    fireEvent.click(screen.getByRole('radio', { name: '디저트' }))
    for (let step = 0; step < 4; step++) fireEvent.click(screen.getByRole('button', { name: '다음' }))
    fireEvent.click(screen.getByRole('button', { name: '완료' }))
    expect(await screen.findByText('메뉴 종류를 확인해 주세요.')).toBeVisible()
    expect(screen.getByRole('radio', { name: '디저트' })).toBeChecked()
    expect(recommend).not.toHaveBeenCalled()
  })

  it('keeps a card on error, shows exhaustion and allows an explicit reset', async () => {
    const api = createMockApi()
    await api.getSession()
    await api.login({ loginId: 'member', password: 'test-password' })
    await api.savePreferences(submission(questionnaire, {}))
    const first = (await api.recommend([])).menu!
    const recommend = vi.spyOn(api, 'recommend')
      .mockResolvedValueOnce({ menu: { ...first, imageUrl: null, cafe: { ...first.cafe, location: null, directionsUrl: null } } })
      .mockRejectedValueOnce(new ApiError(503, 'SERVICE_UNAVAILABLE', '추천을 불러오지 못했어요.'))
      .mockResolvedValueOnce({ menu: null })
    render(<DemoPage api={api} />)
    const card = await screen.findByRole('button', { name: /자세히 보기/ })
    expect(screen.getByRole('img', { name: first.imageAlt })).toHaveAttribute('src', '/illustrations/latte.svg')
    fireEvent.click(card)
    expect(await screen.findByText(first.cafe.roadAddress)).toBeVisible()
    expect(screen.getByRole('button', { name: '주소 복사' })).toBeVisible()
    expect(screen.queryByRole('link', { name: '길찾기' })).not.toBeInTheDocument()
    fireEvent.keyDown(document, { key: 'Escape' })
    fireEvent.click(screen.getByRole('button', { name: '다른 메뉴 보기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('추천을 불러오지 못했어요')
    expect(card).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다른 메뉴 보기' }))
    expect(await screen.findByText('더 추천할 메뉴가 없어요.')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '추천 목록 초기화' }))
    await waitFor(() => expect(recommend).toHaveBeenNthCalledWith(4, []))
  })

  it('stops asking for new cards at 100 and keeps previous cards accessible', async () => {
    const api = createMockApi()
    await api.getSession()
    await api.login({ loginId: 'member', password: 'test-password' })
    await api.savePreferences(submission(questionnaire, {}))
    const first = (await api.recommend([])).menu!
    const onNext = vi.fn()
    const onReset = vi.fn()
    const history = Array.from({ length: 100 }, (_, index) => ({ ...first, id: `test-${index}`, name: `테스트 ${index}` }))
    const page = (cards: typeof history) => <MapExperience user={{ id: 'member', displayName: '회원' }} mock={false}
      history={cards} busy={false} empty={false} error="" onNext={onNext} onReset={onReset} onResurvey={vi.fn()} onLogout={vi.fn()} />
    const { rerender } = render(page(history.slice(0, 99)))
    fireEvent.click(screen.getByRole('button', { name: '다른 메뉴 보기' }))
    expect(onNext).toHaveBeenCalledOnce()
    onNext.mockClear()
    rerender(page(history))
    expect(screen.getByRole('button', { name: '다른 메뉴 보기' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '이전 메뉴' }))
    expect(screen.getByRole('button', { name: '테스트 98 자세히 보기' })).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '다른 메뉴 보기' }))
    expect(screen.getByRole('button', { name: '테스트 99 자세히 보기' })).toBeVisible()
    expect(onNext).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '추천 목록 초기화' }))
    expect(onReset).toHaveBeenCalledOnce()
  })
})
