/** An HTTP error from the backend, carrying the human-readable message the server produced. */
export class ApiError extends Error {
  readonly status: number
  /** Field name -> message, filled for 400 validation failures. */
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH'
  body?: unknown
  headers?: Record<string, string>
}

/**
 * The single place that talks to the backend. Every request goes to /api/... (the Vite dev server
 * proxies that to Spring Boot on :8082), sends/receives JSON, and turns the backend's RFC 7807
 * "problem detail" error bodies into an {@link ApiError}.
 */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    // fetch only rejects when the request could not be made at all (server down, no network).
    throw new ApiError(0, 'Cannot reach the TrackGo backend. Is it running on port 8082?')
  }

  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`.trim()
    let fieldErrors: Record<string, string> = {}
    try {
      const problem = (await response.json()) as { detail?: string; errors?: Record<string, string> }
      message = problem.detail ?? message
      fieldErrors = problem.errors ?? {}
    } catch {
      // The error body was not JSON; keep the status line.
    }
    throw new ApiError(response.status, message, fieldErrors)
  }

  return (await response.json()) as T
}
