import { Text } from '@seed-design/react'
import { ActionButton, BottomSheetRoot, BottomSheetContent, BottomSheetBody, BottomSheetFooter } from '../../../shared/ui'
import { mockMenus, type DemoCafe } from '../mock'
import { directionsUrl, formatPrice } from '../model/demo'
import { Glyph } from './Icons'
import type { Cafe, Menu } from '../api/demoApi'

export function CafeSheet({ cafe, menu, demoCafe, open, onClose, onExited, notify }: {
  cafe: Cafe | undefined
  menu: Menu | undefined
  demoCafe?: DemoCafe
  open: boolean
  onExited: () => void
  onClose: () => void
  notify: (message: string) => void
}) {
  const menus = demoCafe ? mockMenus.filter(item => item.cafeId === demoCafe.id) : []
  async function copyAddress() {
    if (!cafe) return
    try {
      await navigator.clipboard.writeText(cafe.roadAddress)
      notify('주소를 복사했어요.')
    } catch { notify('주소를 복사하지 못했어요. 주소를 길게 눌러 복사해주세요.') }
  }
  return <BottomSheetRoot open={open} onOpenChange={value => { if (!value) onClose() }} onAnimationEnd={value => { if (!value) onExited() }}>
    {cafe && <BottomSheetContent className="cafe-sheet" title={cafe.name} description={demoCafe || menu?.isDemo ? '행궁동 · 시연 자료' : '행궁동 · 카페 정보'} showHandle>
      <BottomSheetBody className="cafe-sheet-body">
        <div className="sheet-address"><Glyph name="pin" size={19} /><span>{cafe.roadAddress}</span><button type="button" className="icon-button" aria-label="주소 복사" onClick={copyAddress}><Glyph name="copy" size={19} /></button></div>
        {menu && <div className="sheet-featured">
          <div className={`menu-art art-${menu.id}`}><img src={menu.imageUrl ?? '/illustrations/latte.svg'} alt={menu.imageAlt} /></div>
          <div><Text as="h3" textStyle="t7Bold">{menu.name}</Text><p>{menu.description}</p></div>
        </div>}
        <div className="sheet-menu-heading"><Text as="h3" textStyle="t6Bold">전체 메뉴</Text>{demoCafe && <span>{menus.length}개</span>}</div>
        {demoCafe ? <ul className="sheet-menu-list">{menus.map(item => <li key={item.id} className={item.id === menu?.id ? 'selected-menu' : ''}>
          <img src={item.imageUrl} alt="" /><div><strong>{item.name}</strong><span>{item.availability === 'SEASON_ENDED' ? '시즌 종료' : '시연 메뉴 · 판매 여부 미확인'}</span></div><span>{formatPrice(item.priceKrw)}</span>
        </li>)}</ul> : <p className="sheet-demo-note">전체 메뉴는 아직 제공되지 않아요.</p>}
      </BottomSheetBody>
      <BottomSheetFooter className="cafe-sheet-footer">
        {cafe.directionsUrl && <ActionButton asChild variant="neutralOutline"><a href={cafe.directionsUrl} target="_blank" rel="noopener noreferrer">길찾기<Glyph name="external" size={16} /></a></ActionButton>}
        {demoCafe && <ActionButton asChild variant="neutralOutline"><a href={directionsUrl('kakao', demoCafe)} target="_blank" rel="noopener noreferrer">카카오 지도<Glyph name="external" size={16} /></a></ActionButton>}
      </BottomSheetFooter>
    </BottomSheetContent>}
  </BottomSheetRoot>
}
