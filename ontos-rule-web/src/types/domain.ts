/**
 * 与后端 DTO 对齐的类型定义。
 * 后端文件参考：ontos-rule-business/src/main/java/com/ontos/rule/biz/web/dto/*.java
 */

export type Severity = 'ERROR' | 'WARN'
export type Backend = 'AUTO' | 'JVM' | 'SQL' | 'SPARK'
export type RuleStatus = 'ACTIVE' | 'DRAFT' | 'DISABLED'
export type RuleMode = 'FORM' | 'CEL'

export interface CheckSpec {
  type: string
  params?: Record<string, unknown>
}

export interface RuleDto {
  id: string
  name: string
  target: string
  expression: string
  formChecks?: CheckSpec[] | null
  mode: RuleMode
  dimensions?: string | null
  severity: Severity
  backendHint: Backend
  status: RuleStatus
  owner: string
  createdAt?: string
  updatedAt?: string
}

export interface RuleRunDto {
  id: number
  ruleId: string
  startedAt: string
  finishedAt?: string
  durationMs?: number
  totalRows?: number
  violationCount?: number
  backendUsed?: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  dataSource?: string
  caller?: string
  error?: string
}

export interface ScoreDto {
  targetType: 'object' | 'project'
  targetId: string
  snapshotAt: string
  overallScore: number
  grade: 'A+' | 'A' | 'B' | 'C' | 'D'
  /** 6 维分数，后端用 map 返回，缺失维度可能为 null/undefined */
  dimensions?: {
    completeness?: number | null
    uniqueness?: number | null
    validity?: number | null
    consistency?: number | null
    accuracy?: number | null
    timeliness?: number | null
  }
  rulesCount?: number
  violationsCount?: number
  totalRows?: number
  triggerType?: string
}

export interface HeatmapDto {
  dimensions: string[]
  objects: string[]
  cells: Array<{ objectId: string; dimension: string; score: number | null }>
}

export interface ProjectScoreResponse {
  project: ScoreDto | null
  objects: ScoreDto[]
}
