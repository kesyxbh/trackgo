import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getAiStatus, sendChatMessage } from '../api/misc.ts'
import type { ToolCall } from '../types.ts'
import { ErrorNote } from './ErrorNote.tsx'

interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  toolCalls?: ToolCall[]
}

/**
 * The delivery assistant: a floating chat panel. It shows which tools the assistant used for every
 * answer (for example "getDeliveryStatus"), which makes it obvious that the answer came from real
 * system data, and which also makes a refused tool call (red) visible.
 */
export function ChatWidget({ customerId }: { customerId: number }) {
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  // One conversation per page load; the server also scopes it to the customer.
  const [conversationId] = useState(() => crypto.randomUUID())
  const bottomRef = useRef<HTMLDivElement | null>(null)

  const status = useQuery({ queryKey: ['ai-status'], queryFn: getAiStatus, staleTime: 60_000 })

  const send = useMutation({
    mutationFn: (text: string) => sendChatMessage(customerId, conversationId, text),
    onSuccess: (response) => {
      setMessages((current) => [
        ...current,
        { role: 'assistant', text: response.reply, toolCalls: response.toolCalls },
      ])
    },
  })

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages, send.isPending, open])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const text = input.trim()
    if (text === '' || send.isPending) return
    setMessages((current) => [...current, { role: 'user', text }])
    setInput('')
    send.mutate(text)
  }

  if (!open) {
    return (
      <button type="button" className="chat-launcher" onClick={() => setOpen(true)}>
        Ask about my delivery
      </button>
    )
  }

  return (
    <section className="chat" aria-label="Delivery assistant">
      <header className="chat__header">
        <strong>Delivery assistant</strong>
        <button type="button" className="chat__close" onClick={() => setOpen(false)} aria-label="Close chat">
          ×
        </button>
      </header>

      {status.data && !status.data.configured && (
        <p className="note note--warn">
          The AI model is not configured on the server (set <code>LLM_API_KEY</code>). Messages will get an
          explanation instead of an answer.
        </p>
      )}

      <div className="chat__log">
        {messages.length === 0 && (
          <p className="muted">
            Try: <em>“Where is my order?”</em> or <em>“How long until my delivery arrives?”</em>
          </p>
        )}
        {messages.map((message, index) => (
          <div key={index} className={`chat__message chat__message--${message.role}`}>
            <p>{message.text}</p>
            {message.toolCalls && message.toolCalls.length > 0 && (
              <p className="chat__tools">
                {message.toolCalls.map((call, callIndex) => (
                  <span key={callIndex} className={`chip ${call.ok ? 'chip--ok' : 'chip--bad'}`}>
                    {call.ok ? 'used' : 'refused'}: {call.name}
                  </span>
                ))}
              </p>
            )}
          </div>
        ))}
        {send.isPending && <p className="muted">Thinking…</p>}
        <ErrorNote error={send.error} />
        <div ref={bottomRef} />
      </div>

      <form className="chat__form" onSubmit={submit}>
        <input
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="Ask about your delivery…"
          maxLength={1000}
          aria-label="Message"
        />
        <button type="submit" disabled={send.isPending || input.trim() === ''}>
          Send
        </button>
      </form>
    </section>
  )
}
