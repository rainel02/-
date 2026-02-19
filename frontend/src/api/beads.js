import { api } from './client';
export const beadApi = {
    list: () => api.get('/beads'),
    tags: () => api.get('/beads/tags'),
    deleteTag: (tag) => api.delete(`/beads/tags/${encodeURIComponent(tag)}`),
    detail: (id) => api.get(`/beads/${id}`),
    create: (payload) => api.post('/beads', payload),
    update: (id, payload) => api.put(`/beads/${id}`, payload),
    remove: (id) => api.delete(`/beads/${id}`),
    updateStatusAndQty: (id, payload) => api.patch(`/beads/${id}/status`, payload),
    saveColors: (id, colors) => api.put(`/beads/${id}/required-colors`, colors),
    saveGridResult: (id, payload) => api.put(`/beads/${id}/grid-result`, payload),
    extractColors: (imageBase64) => api.post('/beads/extract-colors', { imageBase64 }),
    extractColorsDebug: (imageBase64) => api.post('/beads/extract-colors-debug', { imageBase64 }),
    analyzeGrid: (payload) => api.post('/beads/analyze-grid', payload),
    inventory: {
        stock: () => api.get('/inventory/stock'),
        usage: () => api.get('/inventory/usage'),
        demand: () => api.get('/inventory/demand'),
        todoProjects: () => api.get('/inventory/todo-projects'),
        restock: (payload) => api.post('/inventory/restock', payload),
        updateThresholds: (payload) => api.patch('/inventory/thresholds', payload),
        updateInTotals: (payload) => api.patch('/inventory/in-totals', payload)
    }
};
