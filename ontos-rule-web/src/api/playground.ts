import http from './client'

export interface PlaygroundEvalRequest {
  expression: string
  data: Record<string, unknown>
}

export interface PlaygroundEvalResponse {
  ok: boolean
  result?: boolean
  resultStr?: string
  latencyMs?: number
  backend?: string
  variables?: string[]
  invocationId?: string
  caller?: string
  phase?: 'compile' | 'eval'
  error?: string
  consistency?: {
    jvm: string
    sqlAvailable: boolean
    sparkAvailable: boolean
    note: string
  }
}

export interface InvocationRecord {
  id: string
  caller: string
  user: string
  ruleId: string
  traceId: string
  mode: 'EVAL' | 'EXECUTE'
  backend: string
  expression?: string
  success: boolean
  errorMessage?: string
  latencyMs?: number
  timestamp: string
}

export interface InvocationStats {
  totalInvocations: number
  successCount: number
  failedCount: number
  byCaller: Record<string, number>
  byBackend: Record<string, number>
  byMode: Record<string, number>
}

export interface BenchmarkScenario {
  complexity: 'simple' | 'medium' | 'complex'
  complexityLabel: string
  rowCount: number
  durationMs: number
  opsPerSec: number
  expression: string
}

export interface BenchmarkResponse {
  results: BenchmarkScenario[]
  totalElapsedMs: number
  backend: string
  warmupIterations: number
}

export const playgroundApi = {
  eval: (req: PlaygroundEvalRequest) =>
    http.post<PlaygroundEvalResponse>('/playground/eval', req).then((r) => r.data),
  recentInvocations: (params?: { caller?: string; limit?: number }) =>
    http.get<InvocationRecord[]>('/invocations', { params }).then((r) => r.data),
  invocationStats: () =>
    http.get<InvocationStats>('/invocations/stats').then((r) => r.data),
  benchmark: () => http.post<BenchmarkResponse>('/playground/benchmark', null, { timeout: 60_000 }).then((r) => r.data),
}
