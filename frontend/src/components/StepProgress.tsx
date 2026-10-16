import { STATUS_LABEL, STEPS, stepIndex } from '../lib/format.ts'
import type { DeliveryStatus } from '../types.ts'

/** The four delivery stages as a horizontal tracker: done, current, upcoming. */
export function StepProgress({ status }: { status: DeliveryStatus }) {
  const current = stepIndex(status)
  return (
    <ol className="steps" aria-label="Delivery progress">
      {STEPS.map((step, index) => {
        const state = index < current ? 'done' : index === current ? 'current' : 'todo'
        return (
          <li key={step} className={`steps__item steps__item--${state}`} aria-current={state === 'current'}>
            <span className="steps__dot" />
            <span className="steps__label">{STATUS_LABEL[step]}</span>
          </li>
        )
      })}
    </ol>
  )
}
