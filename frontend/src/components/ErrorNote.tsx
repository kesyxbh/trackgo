import { ApiError } from '../api/client.ts'

/** Renders any thrown value as a small red note; renders nothing when there is no error. */
export function ErrorNote({ error }: { error: unknown }) {
  if (error === null || error === undefined) return null
  const message = error instanceof ApiError ? error.message : error instanceof Error ? error.message : String(error)
  return (
    <p className="note note--error" role="alert">
      {message}
    </p>
  )
}
