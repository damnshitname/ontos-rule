import http from './client'
import type { CheckSpec, RuleDto, RuleRunDto } from '@/types/domain'

export interface RulePreviewResponse {
  compiledExpression: string
  variables: string[]
  inferredDimensions: string[]
  valid: boolean
  errorMessage?: string | null
}

export const rulesApi = {
  list: () => http.get<RuleDto[]>('/rules').then((r) => r.data),
  get: (id: string) => http.get<RuleDto>(`/rules/${id}`).then((r) => r.data),
  create: (body: Partial<RuleDto> & { formChecks?: unknown[] }) =>
    http.post<RuleDto>('/rules', body).then((r) => r.data),
  update: (id: string, body: Partial<RuleDto>) =>
    http.put<RuleDto>(`/rules/${id}`, body).then((r) => r.data),
  remove: (id: string) => http.delete<void>(`/rules/${id}`).then((r) => r.data),
  execute: (id: string, dataSource: string) =>
    http
      .post<RuleRunDto>(`/rules/${id}/execute`, { dataSource })
      .then((r) => r.data),
  preview: (body: {
    formChecks?: CheckSpec[] | null
    expression?: string | null
    target?: string | null
    dimensions?: string | null
  }) => http.post<RulePreviewResponse>('/rules/preview', body).then((r) => r.data),
}
