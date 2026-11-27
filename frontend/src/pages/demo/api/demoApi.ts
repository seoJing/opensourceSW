export type Session = {
  authenticated: boolean
  user: { id: string; displayName: string } | null
  csrfToken: string
}

export type Login = { loginId: string; password: string }
export type Question = {
  id: string
  label: string
  type: 'single' | 'multiple'
  minSelections: number
  maxSelections: number
  options: { id: string; label: string }[]
}
export type Questionnaire = { version: string; questions: Question[] }
export type Answer = { questionId: string; optionIds: string[] }
export type Submission = { questionnaireVersion: string; answers: Answer[] }
export type Preferences = {
  saved: boolean
  questionnaireVersion: string | null
  answers: Answer[]
  updatedAt: string | null
}
export type Cafe = {
  id: string
  name: string
  roadAddress: string
  location: { latitude: number; longitude: number } | null
  directionsUrl: string | null
}
export type Menu = {
  id: string
  cafeId: string
  name: string
  description: string
  imageUrl: string | null
  imageAlt: string
  isDemo: boolean
  cafe: Cafe
}
export type Recommendation = { menu: Menu | null }
export type AnswerError = { field: string; questionId: string | null; reason: string; message: string }

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly errors: AnswerError[] = [],
    readonly retryAfter = 0,
  ) {
    super(message)
  }
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isText(value: unknown): value is string {
  return typeof value === 'string'
}

function isSession(value: unknown): value is Session {
  return record(value) && typeof value.authenticated === 'boolean' && isText(value.csrfToken) && value.csrfToken.length > 0 &&
    (value.authenticated
      ? record(value.user) && isText(value.user.id) && isText(value.user.displayName)
      : value.user === null)
}

function isAnswerError(value: unknown): value is AnswerError {
  return record(value) && isText(value.field) && (value.questionId === null || isText(value.questionId)) &&
    isText(value.reason) && isText(value.message)
}

function isQuestionnaire(value: unknown): value is Questionnaire {
  return record(value) && isText(value.version) && Array.isArray(value.questions) && value.questions.length > 0 &&
    value.questions.every(question => record(question) && isText(question.id) && isText(question.label) &&
      (question.type === 'single' || question.type === 'multiple') &&
      typeof question.minSelections === 'number' && Number.isInteger(question.minSelections) && question.minSelections >= 0 &&
      typeof question.maxSelections === 'number' && Number.isInteger(question.maxSelections) && question.maxSelections >= question.minSelections &&
      (question.type === 'multiple' || question.maxSelections <= 1) &&
      Array.isArray(question.options) && question.options.length >= Math.max(1, question.maxSelections) &&
      question.options.every(option => record(option) && isText(option.id) && isText(option.label)))
}

function isPreferences(value: unknown): value is Preferences {
  return record(value) && typeof value.saved === 'boolean' &&
    (value.questionnaireVersion === null || isText(value.questionnaireVersion)) &&
    (value.updatedAt === null || isText(value.updatedAt)) && Array.isArray(value.answers) &&
    value.answers.every(answer => record(answer) && isText(answer.questionId) && Array.isArray(answer.optionIds) && answer.optionIds.every(isText))
}

function isRecommendation(value: unknown): value is Recommendation {
  if (!record(value)) return false
  const menu = value.menu
  if (menu === null) return true
  if (!record(menu) || !record(menu.cafe)) return false
  const { cafe } = menu
  return [menu.id, menu.cafeId, menu.name, menu.description, menu.imageAlt, cafe.id, cafe.name, cafe.roadAddress].every(isText) &&
    menu.cafeId === cafe.id && (menu.imageUrl === null || isText(menu.imageUrl)) && typeof menu.isDemo === 'boolean' &&
    (cafe.directionsUrl === null || (isText(cafe.directionsUrl) && /^https?:\/\//i.test(cafe.directionsUrl))) &&
    (cafe.location === null || (record(cafe.location) &&
      typeof cafe.location.latitude === 'number' && Math.abs(cafe.location.latitude) <= 90 &&
      typeof cafe.location.longitude === 'number' && Math.abs(cafe.location.longitude) <= 180))
}

function checked<T>(value: unknown, validate: (value: unknown) => value is T): T {
  if (!validate(value)) throw new ApiError(200, 'INVALID_RESPONSE', '서버 응답 형식을 확인해 주세요.')
  return value
}

export function createDemoApi(transport: typeof fetch = (...args) => fetch(...args)) {
  let csrfToken = ''
  async function request(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<unknown> {
    const headers = new Headers({ Accept: 'application/json' })
    if (method !== 'GET') headers.set('X-CSRF-TOKEN', csrfToken)
    if (body !== undefined) headers.set('Content-Type', 'application/json')
    const timeout = AbortSignal.timeout(15000)
    const response = await transport('/api/v1' + path, {
      method, headers, credentials: 'same-origin', signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    const retryAfter = Number(response.headers.get('Retry-After'))
    if (response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase() !== 'application/json') {
      throw new ApiError(response.status, 'INVALID_RESPONSE', '서버에서 JSON 응답을 받지 못했어요.')
    }
    const data: unknown = await response.json().catch(() => {
      throw new ApiError(response.status, 'INVALID_RESPONSE', '서버 응답 형식을 확인해 주세요.')
    })
    if (!record(data)) {
      throw new ApiError(response.status, 'INVALID_RESPONSE', '서버 응답 형식을 확인해 주세요.')
    }
    if (response.status !== 200) {
      throw new ApiError(response.status, isText(data.code) ? data.code : 'HTTP_ERROR',
        isText(data.message) ? data.message : '요청을 처리하지 못했어요.',
        Array.isArray(data.errors) ? data.errors.filter(isAnswerError) : [],
        Number.isSafeInteger(retryAfter) && retryAfter > 0 ? retryAfter : 0)
    }
    return data
  }

  async function session(method: string, body?: Login, signal?: AbortSignal) {
    const value = checked(await request('/auth/session', method, body, signal), isSession)
    if (signal?.aborted) throw new DOMException('Request aborted', 'AbortError')
    csrfToken = value.csrfToken
    return value
  }

  return {
    getSession: (signal?: AbortSignal) => session('GET', undefined, signal),
    login: (body: Login) => session('POST', body),
    logout: () => session('DELETE'),
    getQuestionnaire: async () => checked(await request('/preference-questionnaire'), isQuestionnaire),
    getPreferences: async () => checked(await request('/me/preferences'), isPreferences),
    savePreferences: async (body: Submission) => checked(await request('/me/preferences', 'PUT', body), isPreferences),
    recommend: async (excludedMenuIds: string[]) => checked(
      await request('/menu-recommendations', 'POST', { excludedMenuIds }), isRecommendation),
  }
}

export type DemoApi = ReturnType<typeof createDemoApi>
