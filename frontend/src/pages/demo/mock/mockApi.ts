import { createDemoApi, type Preferences, type Questionnaire, type Session, type Submission } from '../api/demoApi'
import { directionsUrl } from '../model/demo'
import { exclusionLimit, showsQuestion } from '../model/preferences'
import { mockCafes, mockMenus } from './index'
import questionnaireData from './questionnaire-v0.1.0.json'

// Questionnaire copied from backend PR #26 at a81f001. The other values are frontend fixtures.
export const questionnaire = questionnaireData as Questionnaire
const initialPreferences: Preferences = { saved: false, questionnaireVersion: null, answers: [], updatedAt: null }
const sessionKey = 'cafe-map-dev-mock-user'
const dataKey = 'cafe-map-dev-mock-preferences'

export function createMockApi(storage?: Storage) {
  const store = storage ?? (typeof window === 'undefined' ? undefined : window.sessionStorage)
  let user = store?.getItem(sessionKey) ?? null
  let savedByUser: Record<string, Preferences> = JSON.parse(store?.getItem(dataKey) ?? '{}')
  let token = crypto.randomUUID()
  const respond = (data: unknown, status = 200) => Response.json(data, { status })
  const error = (status: number, code: string, message: string) => respond({ code, message, errors: [] }, status)
  const session = (): Session => ({
    authenticated: !!user, user: user ? { id: user, displayName: user } : null, csrfToken: token,
  })

  const transport: typeof fetch = async (input, init) => {
    const path = String(input)
    const method = init?.method ?? 'GET'
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
    const auth = path === '/api/v1/auth/session'
    if (!user && !(auth && (method === 'GET' || method === 'POST'))) {
      return error(401, 'AUTHENTICATION_REQUIRED', '로그인이 필요해요.')
    }
    if (method !== 'GET' && new Headers(init?.headers).get('X-CSRF-TOKEN') !== token) {
      return error(403, 'CSRF_INVALID', '세션을 다시 확인해 주세요.')
    }
    if (auth) {
      if (method === 'POST') {
        if (!body?.loginId?.trim() || !body?.password) return error(401, 'INVALID_CREDENTIALS', '로그인 정보를 확인해 주세요.')
        user = body.loginId
        store?.setItem(sessionKey, body.loginId)
      }
      if (method === 'DELETE') {
        user = null
        store?.removeItem(sessionKey)
      }
      if (method !== 'GET') token = crypto.randomUUID()
      return respond(session())
    }
    const userId = user!
    const saved = Object.hasOwn(savedByUser, userId) ? savedByUser[userId] : initialPreferences
    if (path === '/api/v1/preference-questionnaire') return respond(questionnaire)
    if (path === '/api/v1/me/preferences') {
      if (method === 'GET') return respond(saved)
      const request = body as Submission
      if (request.questionnaireVersion !== questionnaire.version) {
        return error(409, 'QUESTIONNAIRE_VERSION_MISMATCH', '새 설문에 응답해 주세요.')
      }
      const selections = Object.fromEntries(request.answers.map(answer => [answer.questionId, answer.optionIds]))
      const invalid = questionnaire.questions.find(question => {
        const selected = selections[question.id]
        return !selected || selected.length < question.minSelections || selected.length > question.maxSelections ||
          new Set(selected).size !== selected.length || selected.some(id => !question.options.some(option => option.id === id)) ||
          (!showsQuestion(questionnaire, selections, question.id) && selected.length > 0)
      })
      if (invalid || request.answers.length !== questionnaire.questions.length) {
        return respond({ code: 'INVALID_ANSWERS', message: '설문 응답을 확인해 주세요.', errors: [{
          field: 'answers', questionId: invalid?.id ?? null, reason: 'SELECTION_COUNT', message: '선택한 답변을 확인해 주세요.',
        }] }, 422)
      }
      const next = { saved: true, ...request, updatedAt: new Date().toISOString() }
      savedByUser = { ...savedByUser, [userId]: next }
      store?.setItem(dataKey, JSON.stringify(savedByUser))
      return respond(next)
    }
    if (path === '/api/v1/menu-recommendations') {
      const ids: string[] = body?.excludedMenuIds
      if (!Array.isArray(ids) || ids.length > exclusionLimit || new Set(ids).size !== ids.length ||
        ids.some(id => typeof id !== 'string' || !id.trim())) {
        return error(400, 'INVALID_REQUEST', '추천 요청 형식을 확인해 주세요.')
      }
      if (!saved.saved || saved.questionnaireVersion !== questionnaire.version) return error(409, 'PREFERENCES_REQUIRED', '현재 설문에 응답해 주세요.')
      const menu = mockMenus.find(item => item.availability !== 'SEASON_ENDED' && !ids.includes(item.id))
      if (!menu) return respond({ menu: null })
      const cafe = mockCafes.find(item => item.id === menu.cafeId)!
      const { id, name, cafeId, description, imageUrl, imageAlt, isDemo } = menu
      return respond({ menu: {
        id, name, cafeId, description, imageUrl, imageAlt, isDemo,
        cafe: { ...cafe, directionsUrl: directionsUrl('naver', cafe) },
      } })
    }
    return error(404, 'NOT_FOUND', '요청 경로를 확인해 주세요.')
  }

  return createDemoApi(transport)
}
