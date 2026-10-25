import type { Problem } from './types'

/** An RFC 9457 problem returned by the API, or a network failure (status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly problem: Problem

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? 'Request failed')
    this.status = problem.status
    this.code = problem.code
    this.problem = problem
  }

  fieldErrors(): Record<string, string> {
    return Object.fromEntries((this.problem.errors ?? []).map((e) => [e.field, e.message]))
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

let csrfReady: Promise<void> | null = null

/** Ensures the XSRF-TOKEN cookie exists; Spring Security issues it on GET /auth/csrf. */
export function ensureCsrf(force = false): Promise<void> {
  if (force || !readCookie('XSRF-TOKEN')) {
    csrfReady = null
  }
  csrfReady ??= fetch('/api/v1/auth/csrf', { credentials: 'same-origin' }).then(() => undefined)
  return csrfReady
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export async function api<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  let payload: BodyInit | undefined
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  if (method !== 'GET') {
    await ensureCsrf()
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  let response: Response
  try {
    response = await fetch('/api/v1' + path, { method, headers, body: payload, credentials: 'same-origin' })
  } catch {
    throw new ApiError({
      status: 0,
      code: 'network_error',
      detail: 'Cannot reach the server. Check your connection.',
    })
  }
  if (response.status === 403 && method !== 'GET') {
    // A stale CSRF token (e.g. after login in another tab): refresh once and retry.
    const retried = await retryWithFreshCsrf(method, path, headers, payload)
    if (retried) response = retried
  }
  if (response.status === 204) return undefined as T
  const text = await response.text()
  const data: unknown = text ? safeJson(text) : undefined
  if (!response.ok) {
    const problem = (data as Problem | undefined) ?? {
      status: response.status,
      code: 'http_' + response.status,
      detail: 'The server answered with an unexpected error.',
    }
    throw new ApiError({
      ...problem,
      status: response.status,
      code: problem.code ?? 'http_' + response.status,
    })
  }
  return data as T
}

async function retryWithFreshCsrf(
  method: Method,
  path: string,
  headers: Record<string, string>,
  payload: BodyInit | undefined,
): Promise<Response | null> {
  await ensureCsrf(true)
  const token = readCookie('XSRF-TOKEN')
  if (!token || token === headers['X-XSRF-TOKEN']) return null
  return fetch('/api/v1' + path, {
    method,
    headers: { ...headers, 'X-XSRF-TOKEN': token },
    body: payload,
    credentials: 'same-origin',
  })
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

export function toQuery(params: Record<string, string | number | undefined | null>): string {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v))
  }
  const s = q.toString()
  return s ? '?' + s : ''
}
