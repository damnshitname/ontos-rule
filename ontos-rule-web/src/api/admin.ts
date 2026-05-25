import http from './client'

export interface ReseedResult {
  wiped: boolean
  rules: number
  weights: number
  runs: number
  violations: number
  scores: number
  message: string
}

export const adminApi = {
  /** 一键重置 demo 数据。wipe=true 清空后种入；false 仅补缺 */
  async reseed(wipe = true): Promise<ReseedResult> {
    const { data } = await http.post<ReseedResult>('/admin/reseed', null, {
      params: { wipe },
    })
    return data
  },
}
