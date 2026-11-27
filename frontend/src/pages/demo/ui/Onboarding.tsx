import { useEffect, useRef, useState } from 'react'
import type { FormEvent, CSSProperties } from 'react'
import { ActionButton, TextField, TextFieldInput } from '../../../shared/ui'
import { mockQuestionnaire } from '../mock'
import { toggleAnswer, type Answers } from '../model/demo'
import type { AnswerError, Login, Questionnaire } from '../api/demoApi'
import { showsQuestion } from '../model/preferences'
import { Glyph } from './Icons'

const noErrors: AnswerError[] = []

export function Brand({ compact = false }: { compact?: boolean }) {
  return <span className={`demo-brand${compact ? ' compact' : ''}`}><span className="brand-mark"><Glyph name="cup" size={22} /></span>카페 지도</span>
}

export function EntryFlow({ stage, onStage, onLogin, onStart, connected = false, mock = false, busy = false, error, blocked = false, onRetry, questionCount, onBack }: {
  stage: 'cover' | 'login' | 'welcome'
  onStage: (stage: 'cover' | 'login') => void
  onLogin: (input: Login) => void
  onStart: () => void
  connected?: boolean
  mock?: boolean
  busy?: boolean
  error?: string
  blocked?: boolean
  onRetry?: () => void
  questionCount?: number
  onBack?: () => void
}) {
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [stage])
  function login(event: FormEvent) {
    event.preventDefault()
    if (!loginId.trim() || !password || busy || blocked) return
    onLogin({ loginId, password })
    setPassword('')
  }
  return <main className={`entry-page stage-${stage}`}>
    <header className="entry-header"><Brand /><span className="neighborhood-label">HAENGGUNG-DONG</span></header>
    {stage === 'cover' ? <div className="cover-layout entrance" key="cover">
      <div className="cover-copy"><h1 ref={heading} tabIndex={-1}>행궁동<br /><span>카페 탐색</span></h1><ActionButton variant="brandSolid" size="large" className="cover-start" disabled={busy} onClick={() => onStage('login')}>시작하기<Glyph name="arrow" /></ActionButton></div>
      <div className="cover-scene" aria-hidden="true"><div className="scene-orbit orbit-one" /><div className="scene-orbit orbit-two" /><div className="scene-map"><img src="/illustrations/cover-map.svg" alt="" /></div><div className="scene-cup"><img src="/illustrations/latte.svg" alt="" /></div><span className="scene-location"><Glyph name="pin" size={18} />행궁동</span><span className="scene-spark spark-one">✳</span><span className="scene-spark spark-two">✦</span><span className="scene-pin pin-one"><Glyph name="cup" size={20} /></span><span className="scene-pin pin-two"><Glyph name="cup" size={18} /></span></div>
    </div> : stage === 'login' ? <section className="flow-panel entrance" key="login">
      <button type="button" className="icon-button flow-back" aria-label="표지로 돌아가기" disabled={busy} onClick={() => onStage('cover')}><Glyph name="back" /></button>
      <h1 ref={heading} tabIndex={-1}>로그인</h1>
      <form onSubmit={login} className="login-form">
        <TextField label="아이디" value={loginId} onValueChange={({ value }) => setLoginId(value)}><TextFieldInput name="loginId" autoComplete="off" placeholder="아이디를 입력해주세요" maxLength={128} required /></TextField>
        <TextField label="비밀번호" value={password} onValueChange={({ value }) => setPassword(value)}><TextFieldInput name="password" type="password" autoComplete="off" placeholder="비밀번호를 입력해주세요" required /></TextField>
        {!connected && <p className="login-hint">시연에서는 원하는 아이디와 비밀번호로 시작할 수 있어요.</p>}
        {mock && <p className="login-hint">개발용 Mock입니다. 테스트용 아이디와 비밀번호를 입력해 주세요.</p>}
        <ActionButton type="submit" variant="brandSolid" size="large" disabled={!loginId.trim() || !password || busy || blocked}>로그인<Glyph name="arrow" /></ActionButton>
      </form>
    </section> : <section className="flow-panel welcome-panel entrance" key="welcome">
      <span className="welcome-symbol" aria-hidden="true">✳</span><h1 ref={heading} tabIndex={-1}>취향 설문</h1><p>메뉴 탐색에 사용할 취향을 선택해주세요.</p><div className="welcome-meta"><span>{questionCount ?? 5}개의 질문</span></div><ActionButton variant="brandSolid" size="large" disabled={busy} onClick={onStart}>설문 시작<Glyph name="arrow" /></ActionButton>
      {onBack && <button type="button" className="text-button" disabled={busy} onClick={onBack}>로그아웃</button>}
    </section>}
    {(busy || error || onRetry) && <div className="entry-feedback">
      {busy && <p role="status">서버 응답을 기다리고 있어요…</p>}
      {error && <p role="alert">{error}</p>}
      {onRetry && <button type="button" className="text-button" disabled={busy} onClick={onRetry}>다시 확인</button>}
    </div>}
    {!connected && <footer className="entry-footer"><span>중간 발표용 시연</span></footer>}
  </main>
}

export function Survey({ initialAnswers, editing, onComplete, onCancel, questionnaire, busy, error, errors = noErrors }: {
  initialAnswers: Answers
  editing: boolean
  onComplete: (answers: Answers) => void
  onCancel: () => void
  questionnaire?: Questionnaire
  busy?: boolean
  error?: string
  errors?: AnswerError[]
}) {
  const [answers, setAnswers] = useState<Answers>(() => Object.fromEntries(Object.entries(initialAnswers).map(([key, value]) => [key, [...value]])))
  const [index, setIndex] = useState(0)
  const [backward, setBackward] = useState(false)
  const heading = useRef<HTMLHeadingElement>(null)
  const questions = questionnaire
    ? questionnaire.questions.filter(question => showsQuestion(questionnaire, answers, question.id))
    : mockQuestionnaire.questions
  const errorIndex = questions.findIndex(question => errors.some(item => item.questionId === question.id))
  const [handledErrors, setHandledErrors] = useState(errors)
  if (errors.length && handledErrors !== errors) {
    setHandledErrors(errors)
    if (errorIndex >= 0) setIndex(errorIndex)
  }
  const question = questions[index]
  const selected = answers[question.id] ?? []
  const questionErrors = errors.filter(item => item.questionId === question.id)
  const last = index === questions.length - 1
  useEffect(() => { heading.current?.focus() }, [index, errors])
  function choose(optionId: string) {
    setAnswers(previous => {
      const current = previous[question.id] ?? []
      let next: string[]
      if (!questionnaire) next = toggleAnswer(mockQuestionnaire.questions[index], current, optionId)
      else if (current.includes(optionId)) next = current.filter(id => id !== optionId)
      else if (question.type === 'single') next = question.maxSelections > 0 ? [optionId] : []
      else next = current.length < question.maxSelections ? [...current, optionId] : current
      const updated = { ...previous, [question.id]: next }
      if (questionnaire && !showsQuestion(questionnaire, updated, 'Q4')) delete updated.Q4
      return updated
    })
  }
  function next() {
    if (busy || selected.length < question.minSelections || selected.length > question.maxSelections) return
    if (last) onComplete(answers)
    else { setBackward(false); setIndex(value => value + 1) }
  }
  function previous() {
    if (index === 0) onCancel()
    else { setBackward(true); setIndex(value => value - 1) }
  }
  return <main className="survey-page">
    <header className="survey-header"><button type="button" className="icon-button" aria-label="이전 단계" disabled={busy} onClick={previous}><Glyph name="back" /></button><Brand compact /><button type="button" className="text-button" disabled={busy} onClick={onCancel}>{editing ? '취소' : '나가기'}</button></header>
    <div className="survey-progress" aria-label={`질문 ${index + 1} / ${questions.length}`}><span style={{ width: `${(index + 1) * 100 / questions.length}%` }} /></div>
    <section className={`question-panel${backward ? ' backwards' : ''}`} key={question.id}>
      <div className="question-count"><span>취향 설문</span><span><b>{String(index + 1).padStart(2, '0')}</b> / {String(questions.length).padStart(2, '0')}</span></div>
      <h1 ref={heading} tabIndex={-1}>{question.label}</h1>
      {'description' in question && <p id="question-help">{String(question.description)}</p>}
      <fieldset className="answer-options" disabled={busy} aria-describedby={questionErrors.length ? 'question-errors' : !questionnaire ? 'question-help' : undefined}><legend className="visually-hidden">{question.label}</legend>{question.options.map((option, optionIndex) => {
        const checked = selected.includes(option.id)
        const disabled = !checked && (question.maxSelections === 0 || (question.type === 'multiple' && selected.length >= question.maxSelections && (!!questionnaire || option.id !== 'any')))
        return <label className={`answer-option${checked ? ' is-selected' : ''}`} key={option.id} style={{ '--option-index': optionIndex } as CSSProperties}>
          <input type={question.type === 'single' ? 'radio' : 'checkbox'} name={question.id} value={option.id} checked={checked} disabled={disabled} onChange={() => choose(option.id)} />
          {'symbol' in option && <span className="answer-symbol" aria-hidden="true">{String(option.symbol)}</span>}<span>{option.label}</span><span className="answer-check" aria-hidden="true"><Glyph name="check" size={18} /></span>
        </label>
      })}</fieldset>
      {questionnaire && question.type === 'single' && question.minSelections === 0 && selected.length > 0 &&
        <button type="button" className="text-button" disabled={busy} onClick={() => choose(selected[0])}>선택 해제</button>}
      {questionErrors.length > 0 && <ul id="question-errors" role="alert">{questionErrors.map(item => <li key={item.field}>{item.message}</li>)}</ul>}
      {error && <p role="alert">{error}</p>}
    </section>
    <div className="survey-bottom"><ActionButton variant="brandSolid" size="large" disabled={busy || selected.length < question.minSelections || selected.length > question.maxSelections} onClick={next}>{busy ? '저장 중…' : last ? '완료' : '다음'}<Glyph name="arrow" /></ActionButton></div>
  </main>
}
