import { describe, expect, it, vi } from 'vitest'
import { createDemoApi } from './demoApi'

describe('demo API', () => {
  it('uses a same-origin cookie and rotates CSRF tokens across login and logout', async () => {
    const guest = { authenticated: false, user: null }
    const user = { authenticated: true, user: { id: 'member', displayName: '회원' } }
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ ...guest, csrfToken: 'before' }))
      .mockResolvedValueOnce(Response.json({ ...user, csrfToken: 'after' }))
      .mockResolvedValueOnce(Response.json({ menu: null }))
      .mockResolvedValueOnce(Response.json({ ...guest, csrfToken: 'guest' }))
    const api = createDemoApi(fetcher)
    await api.getSession()
    await api.login({ loginId: 'member', password: 'test-password' })
    await api.recommend(['previous'])
    await api.logout()
    expect(fetcher.mock.calls.map(([url, init]) => [url, init?.method, init?.credentials, new Headers(init?.headers).get('X-CSRF-TOKEN')])).toEqual([
      ['/api/v1/auth/session', 'GET', 'same-origin', null],
      ['/api/v1/auth/session', 'POST', 'same-origin', 'before'],
      ['/api/v1/menu-recommendations', 'POST', 'same-origin', 'after'],
      ['/api/v1/auth/session', 'DELETE', 'same-origin', 'after'],
    ])
    expect(JSON.parse(String(fetcher.mock.calls[1][1]?.body))).toEqual({ loginId: 'member', password: 'test-password' })
    expect(JSON.parse(String(fetcher.mock.calls[2][1]?.body))).toEqual({ excludedMenuIds: ['previous'] })
  })

  it('rejects SPA HTML and exposes structured HTTP errors and Retry-After', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response('<html></html>', { headers: { 'Content-Type': 'text/html' } }))
      .mockResolvedValueOnce(Response.json({ code: 'TOO_MANY_ATTEMPTS', message: '제한', errors: [] }, {
        status: 429, headers: { 'Retry-After': '12' },
      }))
    const api = createDemoApi(fetcher)
    await expect(api.getSession()).rejects.toMatchObject({ status: 200, code: 'INVALID_RESPONSE' })
    await expect(api.login({ loginId: 'member', password: 'test-password' })).rejects.toMatchObject({
      status: 429, code: 'TOO_MANY_ATTEMPTS', retryAfter: 12,
    })
  })

  it('preserves the status of malformed JSON and rejects invalid session payloads', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response('{', { status: 503, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(Response.json({ authenticated: true, user: null, csrfToken: 'test-token' }))
    const api = createDemoApi(fetcher)
    await expect(api.getSession()).rejects.toMatchObject({ status: 503, code: 'INVALID_RESPONSE' })
    await expect(api.getSession()).rejects.toMatchObject({ status: 200, code: 'INVALID_RESPONSE' })
  })
})
