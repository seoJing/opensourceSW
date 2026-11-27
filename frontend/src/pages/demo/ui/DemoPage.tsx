import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { ApiError, createDemoApi, type AnswerError, type DemoApi, type Login, type Menu, type Questionnaire, type Session } from '../api/demoApi'
import type { Answers } from '../model/demo'
import { exclusionLimit, submission } from '../model/preferences'
import { EntryFlow, Survey } from './Onboarding'
import './demo.css'

const MapExperience = lazy(() => import('./MapExperience').then(module => ({ default: module.MapExperience })))
const devMock = import.meta.env.DEV && import.meta.env.VITE_DEMO_API_MOCK === 'true'
const defaultApi = devMock
  ? import('../mock/mockApi').then(({ createMockApi }) => createMockApi())
  : Promise.resolve(createDemoApi())
const noErrors: AnswerError[] = []
type Phase = 'cover' | 'login' | 'welcome' | 'survey' | 'map'

export default function DemoPage({ api = defaultApi, mock = devMock }: { api?: DemoApi | Promise<DemoApi>; mock?: boolean }) {
  const [phase, setPhase] = useState<Phase>('cover')
  const [session, setSession] = useState<Session>()
  const [questionnaire, setQuestionnaire] = useState<Questionnaire>()
  const [answers, setAnswers] = useState<Answers>({})
  const [editing, setEditing] = useState(false)
  const [history, setHistory] = useState<Menu[]>([])
  const [empty, setEmpty] = useState(false)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState('')
  const [errors, setErrors] = useState(noErrors)
  const [blocked, setBlocked] = useState(false)
  const pending = useRef(true)
  const retryTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => {
    const controller = new AbortController()
    async function start() {
      let current: Session | undefined
      try {
        const client = await api
        if (controller.signal.aborted) return
        current = await client.getSession(controller.signal)
        if (controller.signal.aborted) return
        setSession(current)
        if (current.authenticated) {
          setPhase('welcome')
          const setup = await loadPreferences(client)
          if (controller.signal.aborted) return
          setQuestionnaire(setup.definition)
          setAnswers(setup.answers)
          setPhase(setup.restored ? 'map' : 'welcome')
          if (setup.stale) setError('설문이 변경됐어요. 새 설문에 응답해 주세요.')
          if (setup.restored) {
            const result = await client.recommend([])
            if (controller.signal.aborted) return
            setHistory(result.menu ? [result.menu] : [])
            setEmpty(!result.menu)
          }
        }
      } catch (problem) {
        if (!controller.signal.aborted) {
          if (problem instanceof ApiError && problem.code === 'AUTHENTICATION_REQUIRED') {
            setSession(undefined); setQuestionnaire(undefined); setAnswers({}); setPhase('login')
          }
          if (problem instanceof ApiError && problem.code === 'PREFERENCES_REQUIRED') {
            setQuestionnaire(undefined)
            setAnswers({})
            setPhase('welcome')
            try {
              const definition = await (await api).getQuestionnaire()
              if (!controller.signal.aborted) setQuestionnaire(definition)
            } catch (refreshError) {
              if (controller.signal.aborted) return
              if (refreshError instanceof ApiError && refreshError.code === 'AUTHENTICATION_REQUIRED') {
                setSession(undefined)
                setPhase('login')
              }
              setError(messageFor(refreshError))
              return
            }
          }
          if (!controller.signal.aborted) setError(messageFor(problem))
        }
      } finally {
        if (!controller.signal.aborted) { pending.current = false; setBusy(false) }
      }
    }
    void start()
    return () => { controller.abort(); clearTimeout(retryTimer.current) }
  }, [api])

  function clearPersonal() {
    setQuestionnaire(undefined)
    setAnswers({})
    setHistory([])
    setEmpty(false)
    setEditing(false)
  }

  async function syncPreferences(client: DemoApi, enterSurvey = false) {
    const setup = await loadPreferences(client)
    setQuestionnaire(setup.definition)
    setAnswers(setup.answers)
    setPhase(setup.restored ? 'map' : enterSurvey ? 'survey' : 'welcome')
    if (setup.stale) setError('설문이 변경됐어요. 새 설문에 응답해 주세요.')
    if (setup.restored) await recommend(client, [])
  }

  async function recommend(client: DemoApi, previous: Menu[]) {
    if (previous.length >= exclusionLimit) return
    const { menu } = await client.recommend(previous.map(item => item.id))
    if (menu && previous.some(item => item.id === menu.id)) throw new Error('Repeated menu')
    setHistory(menu ? [...previous, menu] : previous)
    setEmpty(!menu)
  }

  async function run(action: (client: DemoApi) => Promise<void>) {
    if (pending.current) return
    pending.current = true
    setBusy(true)
    setError('')
    setErrors(noErrors)
    try {
      await action(await api)
    } catch (problem) {
      setError(messageFor(problem))
      if (problem instanceof ApiError) {
        switch (problem.code) {
          case 'AUTHENTICATION_REQUIRED':
            clearPersonal()
            setSession(undefined)
            setPhase('login')
            break
          case 'CSRF_INVALID':
            try {
              const current = await (await api).getSession()
              if (!current.authenticated || current.user?.id !== session?.user?.id) {
                clearPersonal()
                setPhase(current.authenticated ? 'welcome' : 'login')
              }
              setSession(current)
            } catch {
              setError('세션을 확인하지 못했어요. 다시 확인해 주세요.')
            }
            break
          case 'QUESTIONNAIRE_VERSION_MISMATCH':
          case 'PREFERENCES_REQUIRED':
            clearPersonal()
            setPhase('welcome')
            try {
              setQuestionnaire(await (await api).getQuestionnaire())
            } catch (refreshError) {
              if (refreshError instanceof ApiError && refreshError.code === 'AUTHENTICATION_REQUIRED') {
                setSession(undefined)
                setPhase('login')
              }
              setError(messageFor(refreshError))
            }
            break
          case 'INVALID_ANSWERS':
            setErrors(problem.errors)
            break
          case 'TOO_MANY_ATTEMPTS':
            if (problem.retryAfter) {
              setBlocked(true)
              clearTimeout(retryTimer.current)
              retryTimer.current = setTimeout(() => setBlocked(false), Math.min(problem.retryAfter * 1000, 2147483647))
            }
            break
        }
      }
    } finally {
      pending.current = false
      setBusy(false)
    }
  }

  function login(input: Login) {
    void run(async client => {
      if (!session) await client.getSession()
      const current = await client.login(input)
      clearPersonal()
      setSession(current)
      setPhase('welcome')
      await syncPreferences(client)
    })
  }

  function logout() {
    void run(async client => {
      setSession(await client.logout())
      clearPersonal()
      setPhase('cover')
    })
  }

  if (phase === 'map' && session?.user) return <Suspense fallback={<main className="demo-loading" role="status">행궁동 지도를 펼치고 있어요…</main>}>
    <MapExperience user={session.user} mock={mock} history={history} empty={empty} busy={busy} error={error}
      onNext={() => void run(client => recommend(client, history))}
      onReset={() => void run(async client => {
        const { menu } = await client.recommend([])
        setHistory(menu ? [menu] : [])
        setEmpty(!menu)
      })}
      onResurvey={() => { setEditing(true); setPhase('survey'); setError(''); setErrors(noErrors) }}
      onLogout={logout} />
  </Suspense>

  if (phase === 'survey' && questionnaire) return <Survey key={questionnaire.version} questionnaire={questionnaire}
    initialAnswers={answers} editing={editing} busy={busy} error={error} errors={errors}
    onComplete={draft => void run(async client => {
      const saved = await client.savePreferences(submission(questionnaire, draft))
      setAnswers(toAnswers(saved.answers))
      setEditing(false)
      setHistory([])
      setEmpty(false)
      setPhase('map')
      await recommend(client, [])
    })}
    onCancel={() => { setErrors(noErrors); setError(''); setPhase(editing ? 'map' : 'welcome') }} />

  return <EntryFlow connected mock={mock} stage={phase === 'survey' || phase === 'map' ? 'welcome' : phase}
    busy={busy} blocked={blocked} error={error} questionCount={questionnaire?.questions.length}
    onStage={next => { if (!pending.current) { setError(''); setPhase(next) } }}
    onLogin={login}
    onStart={() => questionnaire ? setPhase('survey') : void run(client => syncPreferences(client, true))}
    onBack={session?.authenticated ? logout : undefined}
    onRetry={error && !session && phase === 'cover' ? () => void run(async client => {
      const current = await client.getSession()
      setSession(current)
      if (current.authenticated) { setPhase('welcome'); await syncPreferences(client) }
    }) : undefined} />
}

function toAnswers(answers: { questionId: string; optionIds: string[] }[]) {
  return Object.fromEntries(answers.map(({ questionId, optionIds }) => [questionId, optionIds]))
}

async function loadPreferences(client: DemoApi) {
  const [definition, saved] = await Promise.all([client.getQuestionnaire(), client.getPreferences()])
  const restored = saved.saved && saved.questionnaireVersion === definition.version
  return { definition, restored, stale: saved.saved && !restored, answers: restored ? toAnswers(saved.answers) : {} }
}

function messageFor(problem: unknown) {
  if (!(problem instanceof ApiError)) return '요청을 처리하지 못했어요. 연결을 확인하고 다시 시도해 주세요.'
  if (problem.code === 'TOO_MANY_ATTEMPTS' && problem.retryAfter) return `${problem.retryAfter}초 후에 다시 로그인해 주세요.`
  if (problem.code === 'CSRF_INVALID') return '세션을 다시 확인했어요. 요청을 다시 시도해 주세요.'
  return problem.message
}
