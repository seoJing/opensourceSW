import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { demoMap } from '../mock'
import type { Cafe } from '../api/demoApi'
import { Glyph } from './Icons'

type Props = {
  cafes: Pick<Cafe, 'id' | 'name' | 'location'>[]
  activeCafeId: string | null
  exploring: boolean
  onSelect: (id: string) => void
  notify: (message: string) => void
}

function pinIcon(cafe: Pick<Cafe, 'name'>, active: boolean) {
  const pin = document.createElement('div')
  pin.className = `cafe-pin${active ? ' is-active' : ''}`
  const label = document.createElement('span')
  label.className = 'cafe-pin-label'
  label.textContent = cafe.name
  const dot = document.createElement('span')
  dot.className = 'cafe-pin-dot'
  dot.textContent = '·'
  pin.append(label, dot)
  return L.divIcon({ html: pin, className: 'cafe-marker', iconSize: [120, 58], iconAnchor: [60, 58] })
}

export function DemoMap({ cafes, activeCafeId, exploring, onSelect, notify }: Props) {
  const element = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const markers = useRef(new Map<string, L.Marker>())
  const userMarker = useRef<L.CircleMarker | null>(null)
  const [locating, setLocating] = useState(false)
  const [zoomState, setZoomState] = useState({ in: true, out: true })

  useEffect(() => {
    if (!element.current) return
    const markerSet = markers.current
    const instance = L.map(element.current, {
      center: demoMap.center, zoom: 16.5, minZoom: 15.75, maxZoom: 18.5, zoomSnap: 0.25,
      zoomControl: false, attributionControl: false, maxBounds: demoMap.bounds, maxBoundsViscosity: 0.9,
    })
    const updateZoom = () => setZoomState({ in: instance.getZoom() < instance.getMaxZoom(), out: instance.getZoom() > instance.getMinZoom() })
    instance.on('zoomend', updateZoom)
    L.imageOverlay(demoMap.imageUrl, demoMap.bounds, { alt: '행궁동 거리 지도', interactive: false }).addTo(instance)
    map.current = instance
    const observer = new ResizeObserver(() => {
      instance.invalidateSize()
      instance.setMinZoom(Math.max(15.75, instance.getBoundsZoom(demoMap.bounds, true)))
      updateZoom()
    })
    observer.observe(element.current)
    return () => {
      observer.disconnect()
      instance.remove()
      map.current = null
      markerSet.clear()
    }
  }, [])

  useEffect(() => {
    const instance = map.current
    if (!instance) return
    const markerSet = markers.current
    for (const marker of markerSet.values()) marker.remove()
    markerSet.clear()
    for (const cafe of cafes) {
      if (!cafe.location) continue
      const marker = L.marker([cafe.location.latitude, cafe.location.longitude], {
        icon: pinIcon(cafe, false), title: `${cafe.name} 위치 보기`, keyboard: true,
      }).addTo(instance).on('click', () => onSelect(cafe.id))
      marker.getElement()?.setAttribute('aria-label', `${cafe.name} 위치 보기`)
      markerSet.set(cafe.id, marker)
    }
    return () => {
      for (const marker of markerSet.values()) marker.remove()
      markerSet.clear()
    }
  }, [cafes, onSelect])

  useEffect(() => {
    const instance = map.current
    if (!instance) return
    for (const cafe of cafes) {
      const marker = markers.current.get(cafe.id)
      marker?.setIcon(pinIcon(cafe, cafe.id === activeCafeId))
      marker?.setZIndexOffset(cafe.id === activeCafeId ? 1000 : 0)
      marker?.getElement()?.setAttribute('aria-label', `${cafe.name} 위치 보기`)
    }
    const active = cafes.find(cafe => cafe.id === activeCafeId)
    if (active?.location) {
      const point = instance.project([active.location.latitude, active.location.longitude])
      if (exploring) point.y += instance.getSize().y * 0.17
      instance.panTo(instance.unproject(point), { animate: !window.matchMedia('(prefers-reduced-motion: reduce)').matches, duration: 0.45 })
    }
  }, [activeCafeId, cafes, exploring])

  function locate() {
    const instance = map.current
    if (!instance || locating) return
    if (!navigator.geolocation) { notify('이 브라우저에서는 현재 위치를 확인할 수 없어요.'); return }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(position => {
      if (map.current !== instance) return
      setLocating(false)
      const point = L.latLng(position.coords.latitude, position.coords.longitude)
      if (!L.latLngBounds(demoMap.bounds).contains(point)) { userMarker.current?.remove(); userMarker.current = null; notify('현재 위치가 행궁동 지도 범위 밖에 있어요.'); return }
      userMarker.current?.remove()
      userMarker.current = L.circleMarker(point, { radius: 8, color: 'var(--seed-color-bg-layer-default)', weight: 3, fillColor: 'var(--seed-color-bg-informative-solid)', fillOpacity: 1, className: 'my-location-pin' }).addTo(instance).bindTooltip('내 위치')
      instance.panTo(point, { animate: !window.matchMedia('(prefers-reduced-motion: reduce)').matches })
      notify('지도에 내 위치를 표시했어요.')
    }, error => {
      if (map.current !== instance) return
      setLocating(false)
      userMarker.current?.remove()
      userMarker.current = null
      notify(error.code === 1 ? '위치 권한이 꺼져 있어요. 카페는 계속 둘러볼 수 있어요.' : '현재 위치를 확인하지 못했어요. 다시 시도해주세요.')
    }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 0 })
  }

  return <>
    <div ref={element} className="demo-map" aria-label="행궁동 카페 지도" />
    <div className="map-attribution"><a href="https://leafletjs.com" target="_blank" rel="noopener noreferrer">Leaflet</a> · © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap contributors</a></div>
    <div className={`map-tools${exploring ? ' is-exploring' : ''}`}>
      <div className="zoom-tools">
        <button type="button" className="map-tool" aria-label="지도 확대" disabled={!zoomState.in} onClick={() => map.current?.zoomIn()}><Glyph name="plus" size={19} /></button>
        <button type="button" className="map-tool" aria-label="지도 축소" disabled={!zoomState.out} onClick={() => map.current?.zoomOut()}><Glyph name="minus" size={19} /></button>
      </div>
      <button type="button" className="map-tool location-tool" aria-label="내 위치 표시" disabled={locating} onClick={locate}><Glyph name="location" size={21} /></button>
    </div>
  </>
}
