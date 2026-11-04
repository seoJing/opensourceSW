export async function getHealth(): Promise<string | undefined> {
  const response = await fetch('/api/health', {
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const health: unknown = await response.json()
  if (typeof health === 'object' && health !== null && 'status' in health && typeof health.status === 'string') {
    return health.status
  }
}
