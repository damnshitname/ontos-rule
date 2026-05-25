import http from './client'
import type { RuleRunDto } from '@/types/domain'

export interface ViolationDto {
  id: number
  runId: number
  targetPk: string
  violatingValue: string
  context?: string
  sampledAt: string
}

export const runsApi = {
  list: (params?: { ruleId?: string; limit?: number }) =>
    http.get<RuleRunDto[]>('/runs', { params }).then((r) => r.data),
  get: (id: number) =>
    http.get<RuleRunDto>(`/runs/${id}`).then((r) => r.data),
  violations: (id: number) =>
    http.get<ViolationDto[]>(`/runs/${id}/violations`).then((r) => r.data),
}
