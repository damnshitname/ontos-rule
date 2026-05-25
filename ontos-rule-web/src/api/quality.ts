import http from './client'
import type { HeatmapDto, ProjectScoreResponse, ScoreDto } from '@/types/domain'

export const qualityApi = {
  projectScore: () => http.get<ProjectScoreResponse>('/quality/project/score').then((r) => r.data),
  heatmap: () => http.get<HeatmapDto>('/quality/project/heatmap').then((r) => r.data),
  objectScore: (objId: string) =>
    http.get<ScoreDto>(`/quality/objects/${objId}/score`).then((r) => r.data),
  objectHistory: (objId: string, days = 30) =>
    http
      .get<ScoreDto[]>(`/quality/objects/${objId}/score/history`, { params: { days } })
      .then((r) => r.data),
  projectHistory: (days = 30) =>
    http
      .get<ScoreDto[]>('/quality/project/score/history', { params: { days } })
      .then((r) => r.data),
  recompute: (objectId?: string) =>
    http
      .post<{ scope: string; project?: ScoreDto; objects?: number }>(
        '/quality/recompute',
        null,
        { params: objectId ? { objectId } : undefined }
      )
      .then((r) => r.data),
  getWeights: (targetId = '*') =>
    http
      .get<{ targetId: string; weights: Record<string, number> }>('/quality/weights', {
        params: { targetId },
      })
      .then((r) => r.data),
  setWeights: (targetId: string, weights: Record<string, number>) =>
    http
      .put<{ targetId: string; weights: Record<string, number> }>('/quality/weights', {
        targetId,
        weights,
      })
      .then((r) => r.data),
}
