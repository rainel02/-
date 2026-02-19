const API_BASE = window.pindouDesktop?.apiBase || '/api';
async function request(path, init) {
    const resp = await fetch(`${API_BASE}${path}`, {
        headers: { 'Content-Type': 'application/json' },
        ...init
    });
    if (!resp.ok) {
        throw new Error(`请求失败: ${resp.status}`);
    }
    if (resp.status === 204) {
        return undefined;
    }
    const text = await resp.text();
    if (!text) {
        return undefined;
    }
    return JSON.parse(text);
}
export const api = {
    get: (path) => request(path),
    post: (path, body) => request(path, { method: 'POST', body: JSON.stringify(body) }),
    put: (path, body) => request(path, { method: 'PUT', body: JSON.stringify(body) }),
    patch: (path, body) => request(path, { method: 'PATCH', body: JSON.stringify(body) }),
    delete: (path) => request(path, { method: 'DELETE' })
};
