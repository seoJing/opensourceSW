import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Cafe, Menu, Session } from '../api/demoApi'
import { mockCafes, mockMenus } from '../mock'
import { directionsUrl } from '../model/demo'
import { exclusionLimit } from '../model/preferences'
import { Glyph } from './Icons'
import { Brand } from './Onboarding'
import { DemoMap } from './DemoMap'
import { CafeSheet } from './CafeSheet'

type Props = {
  user: NonNullable<Session['user']>
  mock: boolean
  history: Menu[]
  empty: boolean
  busy: boolean
  error: string
  onNext: () => void
  onReset: () => void
  onResurvey: () => void
  onLogout: () => void
}

export function MapExperience({ user, mock, history, empty, busy, error, onNext, onReset, onResurvey, onLogout }: Props) {
  const [query, setQuery] = useState('')
  const [exploring, setExploring] = useState(true)
  const [pastIndex, setPastIndex] = useState<number>()
  const [selected, setSelected] = useState<{ cafe: Cafe; menu?: Menu }>()
  const [sheetOpen, setSheetOpen] = useState(false)
  const [toast, setToast] = useState('')
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const account = useRef<HTMLDetailsElement>(null)
  const opener = useRef<HTMLElement | null>(null)
  const activeIndex = Math.min(pastIndex ?? history.length - 1, history.length - 1)
  const activeMenu = history[activeIndex]
  const normalizedQuery = query.trim().toLocaleLowerCase('ko-KR')
  const cafes = useMemo(() => {
    if (!mock) return [...new Map(history.map(menu => [menu.cafeId, menu.cafe])).values()]
    return mockCafes.filter(cafe => cafe.name.toLocaleLowerCase('ko-KR').includes(normalizedQuery)
      || mockMenus.some(menu => menu.cafeId === cafe.id && menu.name.toLocaleLowerCase('ko-KR').includes(normalizedQuery)))
      .map(cafe => ({ ...cafe, directionsUrl: directionsUrl('naver', cafe) }))
  }, [history, mock, normalizedQuery])
  const notify = useCallback((message: string) => {
    clearTimeout(timer.current)
    setToast(message)
    timer.current = setTimeout(() => setToast(''), 5000)
  }, [])
  const openCafe = useCallback((cafe: Cafe, menu?: Menu) => {
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setSelected({ cafe, menu })
    setSheetOpen(true)
  }, [])
  const selectCafe = useCallback((id: string) => {
    const cafe = cafes.find(item => item.id === id)
    if (cafe) openCafe(cafe, activeMenu?.cafeId === id ? activeMenu : history.find(menu => menu.cafeId === id))
  }, [activeMenu, cafes, history, openCafe])

  useEffect(() => {
    function dismiss(event: PointerEvent) {
      if (event.target instanceof Node && !account.current?.contains(event.target) && account.current) account.current.open = false
    }
    document.addEventListener('pointerdown', dismiss)
    return () => {
      document.removeEventListener('pointerdown', dismiss)
      clearTimeout(timer.current)
    }
  }, [])

  const limitReached = history.length >= exclusionLimit
  const canAdvance = activeIndex < history.length - 1
  return <main className={`map-experience${exploring ? ' exploring' : ''}${selected ? ' has-sheet' : ''}`}>
    <DemoMap cafes={cafes} activeCafeId={selected?.cafe.id ?? (exploring ? activeMenu?.cafeId ?? null : null)} exploring={exploring} onSelect={selectCafe} notify={notify} />
    <div className="map-wordmark"><Brand /></div>
    <header className="map-topbar">
      {exploring && <button type="button" className="map-top-button" aria-label="지도 메인으로 돌아가기" onClick={() => setExploring(false)}><Glyph name="back" /></button>}
      {mock && <div className="map-search"><Glyph name="search" size={21} /><input type="search" aria-label="카페 또는 메뉴 검색" placeholder="카페 또는 메뉴 검색" value={query} onChange={event => setQuery(event.target.value)} />{query && <button type="button" className="icon-button" aria-label="검색어 지우기" onClick={() => setQuery('')}><Glyph name="close" size={17} /></button>}</div>}
      <details className="account-menu" ref={account} onKeyDown={event => { if (event.key === 'Escape' && account.current) { account.current.open = false; account.current.querySelector('summary')?.focus() } }}>
        <summary aria-label="마이페이지"><Glyph name="user" size={23} /></summary>
        <div className="account-popover"><p><strong>{user.displayName}님</strong></p><button type="button" disabled={busy} onClick={onResurvey}><Glyph name="reset" size={19} />설문 다시하기<Glyph name="chevron" size={16} /></button><button type="button" disabled={busy} onClick={onLogout}><Glyph name="logout" size={19} />로그아웃</button></div>
      </details>
    </header>
    <div className="map-area-label"><span className="area-dot" />행궁동{mock && ' · 시연 데이터'}</div>
    {mock && !cafes.length && <div className="map-no-results"><Glyph name="search" size={25} /><strong>찾는 카페가 없어요</strong><button type="button" className="text-button" onClick={() => setQuery('')}>전체 카페 보기</button></div>}
    {exploring ? <section className="menu-explorer" aria-label="메뉴 탐색">
      {activeMenu && <div className="menu-carousel">
        <button type="button" className="menu-card is-active" aria-label={`${activeMenu.name} 자세히 보기`} onClick={() => openCafe(activeMenu.cafe, activeMenu)}>
          <div className={`menu-card-art menu-art art-${activeMenu.id}`}><img src={activeMenu.imageUrl ?? '/illustrations/latte.svg'} alt={activeMenu.imageAlt} /></div>
          <div className="menu-card-copy"><span className="menu-cafe-name">{activeMenu.cafe.name}{activeMenu.isDemo && ' · 시연'}</span>
            <div><h2>{activeMenu.name}</h2><Glyph name="chevron" size={18} /></div><p>{activeMenu.description}</p></div>
        </button>
      </div>}
      {activeMenu && <div className="carousel-navigation">
        <button type="button" aria-label="이전 메뉴" disabled={busy || activeIndex <= 0} onClick={() => setPastIndex(activeIndex - 1)}><Glyph name="back" size={18} /></button>
        <span className="menu-position" aria-live="polite">{activeIndex + 1}<span> / {history.length}</span></span>
        <button type="button" aria-label="다른 메뉴 보기" disabled={busy || (!canAdvance && (empty || limitReached))}
          onClick={() => {
            if (canAdvance) setPastIndex(activeIndex + 1)
            else { setPastIndex(undefined); onNext() }
          }}><Glyph name="arrow" size={18} /></button>
      </div>}
      {busy && <p className="empty-menu-card" role="status">서버 응답을 기다리고 있어요…</p>}
      {error && <p className="empty-menu-card" role="alert">{error}
        {!activeMenu && <button type="button" className="text-button" disabled={busy} onClick={onNext}>추천 다시 요청</button>}
      </p>}
      {(empty || limitReached) && <div className="empty-menu-card" role="status">
        {empty ? '더 추천할 메뉴가 없어요.' : '메뉴 100개를 모두 확인했어요.'}
        <button type="button" className="text-button" disabled={busy} onClick={() => { setPastIndex(undefined); onReset() }}>추천 목록 초기화</button>
      </div>}
    </section> : <div className="explore-launch"><button type="button" className="explore-orb" aria-label="메뉴 탐색 시작" onClick={() => setExploring(true)}><Glyph name="cup" size={30} /><span className="orb-arrow"><Glyph name="arrow" size={13} /></span></button><span className="explore-label">메뉴 탐색</span></div>}
    <CafeSheet cafe={selected?.cafe} menu={selected?.menu} demoCafe={mock ? mockCafes.find(cafe => cafe.id === selected?.cafe.id) : undefined}
      open={sheetOpen} onClose={() => setSheetOpen(false)} onExited={() => { setSelected(undefined); opener.current?.focus() }} notify={notify} />
    <div className={`demo-toast${toast ? ' visible' : ''}`} role="status" aria-live="polite">{toast}</div>
  </main>
}
