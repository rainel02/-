export type BeadStatus = 'DONE' | 'IN_PROGRESS' | 'TODO'

export interface ColorRequirement {
  code: string
  quantity: number
}

export interface ColorExtractionDebugResult {
  colors: ColorRequirement[]
  strategy?: string
  rawText?: string
  locationLines: string[]
  pairLogs: string[]
  fallbackLogs: string[]
}

export interface BeadProject {
  id: number
  name: string
  tags: string[]
  status: BeadStatus
  sourceUrl?: string
  patternImage?: string
  workImage?: string
  quantityDone: number
  quantityPlan: number
  requiredColors: ColorRequirement[]
  gridRows?: number
  gridCols?: number
  gridCellsJson?: string
}

export interface BeadProjectSummary {
  id: number
  name: string
  tags: string[]
  status: BeadStatus
  sourceUrl?: string
  patternImage?: string
  quantityDone: number
  quantityPlan: number
}

export interface InventoryRow {
  code: string
  inTotal: number
  remain: number
  used: number
  warning: string
  alertThreshold: number
}

export interface TodoProjectRow {
  projectId: number
  projectName: string
  projectImage?: string
  projectUrl?: string
  quantityPlan: number
  colors: ColorRequirement[]
}

export interface UsageProjectItem {
  projectId: number
  projectName: string
  projectImage?: string
  projectUrl?: string
  used: number
}

export interface UsageRow {
  code: string
  used: number
  projects: UsageProjectItem[]
}

export interface DemandRow {
  code: string
  remain: number
  need: number
  projects: UsageProjectItem[]
}

export interface GridAnalysisCell {
  row: number
  col: number
  code: string
}

export interface GridAnalysisResult {
  rows: number
  cols: number
  ocrCount: number
  filledCount: number
  cells: GridAnalysisCell[]
}
