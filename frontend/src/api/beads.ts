import { api } from './client'
import type { BeadProject, BeadProjectSummary, ColorRequirement, InventoryRow, UsageRow, DemandRow, GridAnalysisResult, ColorExtractionDebugResult, TodoProjectRow } from '@/types'

export const beadApi = {
  list: () => api.get<BeadProjectSummary[]>('/beads'),
  tags: () => api.get<string[]>('/beads/tags'),
  addTag: (tag: string) => api.post<{ tag: string }>('/beads/tags', { tag }),
  deleteTag: (tag: string) => api.delete<{ affectedProjects: number }>(`/beads/tags/${encodeURIComponent(tag)}`),
  getOcrSettings: () => api.get<{ ak: string; sk: string; ak2: string; sk2: string }>('/beads/ocr-settings'),
  saveOcrSettings: (payload: { ak: string; sk: string; ak2: string; sk2: string }) =>
    api.put<{ ak: string; sk: string; ak2: string; sk2: string }>('/beads/ocr-settings', payload),
  detail: (id: number) => api.get<BeadProject>(`/beads/${id}`),
  create: (payload: Partial<BeadProject>) => api.post<BeadProject>('/beads', payload),
  update: (id: number, payload: Partial<BeadProject>) => api.put<BeadProject>(`/beads/${id}`, payload),
  remove: (id: number) => api.delete<void>(`/beads/${id}`),
  updateStatusAndQty: (id: number, payload: { status: BeadProject['status']; quantityDone: number; quantityPlan: number }) =>
    api.patch<BeadProjectSummary>(`/beads/${id}/status`, payload),
  saveColors: (id: number, colors: ColorRequirement[]) => api.put<BeadProject>(`/beads/${id}/required-colors`, colors),
  saveGridResult: (id: number, payload: { rows: number; cols: number; cells: string[] }) =>
    api.put<BeadProject>(`/beads/${id}/grid-result`, payload),
  extractColors: (payload: {
    imageBase64?: string
    originalImageBase64?: string
    cropRect?: { x: number; y: number; width: number; height: number }
  }) => api.post<ColorRequirement[]>('/beads/extract-colors', payload),
  extractColorsDebug: (payload: {
    imageBase64?: string
    originalImageBase64?: string
    cropRect?: { x: number; y: number; width: number; height: number }
  }) => api.post<ColorExtractionDebugResult>('/beads/extract-colors-debug', payload),
  analyzeGrid: (payload: {
    imageBase64?: string
    originalImageBase64?: string
    cropRect?: { x: number; y: number; width: number; height: number }
    rows: number
    cols: number
    imageWidth: number
    imageHeight: number
    candidateCodes: string[]
    candidateQuantities?: Record<string, number>
    candidateColorHex?: Record<string, string>
  }) => api.post<GridAnalysisResult>('/beads/analyze-grid', payload),
  inventory: {
    stock: () => api.get<InventoryRow[]>('/inventory/stock'),
    usage: () => api.get<UsageRow[]>('/inventory/usage'),
    demand: () => api.get<DemandRow[]>('/inventory/demand'),
    todoProjects: () => api.get<TodoProjectRow[]>('/inventory/todo-projects'),
    restock: (payload: Record<string, number>) => api.post<InventoryRow[]>('/inventory/restock', payload),
    updateThresholds: (payload: Record<string, number>) => api.patch<InventoryRow[]>('/inventory/thresholds', payload),
    updateInTotals: (payload: Record<string, number>) => api.patch<InventoryRow[]>('/inventory/in-totals', payload)
  }
}
