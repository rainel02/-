import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'

let toastTimer: number | null = null

function ensureToastElement() {
	let toast = document.getElementById('app-toast')
	if (toast) return toast

	toast = document.createElement('div')
	toast.id = 'app-toast'
	Object.assign(toast.style, {
		position: 'fixed',
		right: '16px',
		bottom: '16px',
		maxWidth: '420px',
		background: 'rgba(32, 33, 36, 0.94)',
		color: '#fff',
		padding: '10px 12px',
		borderRadius: '8px',
		boxShadow: '0 4px 12px rgba(0,0,0,0.28)',
		fontSize: '13px',
		lineHeight: '1.4',
		zIndex: '99999',
		display: 'none',
		whiteSpace: 'pre-wrap'
	} as CSSStyleDeclaration)

	document.body.appendChild(toast)
	return toast
}

function showToast(message: string) {
	const toast = ensureToastElement()
	toast.textContent = message
	toast.style.display = 'block'

	if (toastTimer !== null) {
		window.clearTimeout(toastTimer)
	}
	toastTimer = window.setTimeout(() => {
		toast.style.display = 'none'
	}, 4200)
}

window.alert = (message?: unknown) => {
	const text = String(message ?? '')
	console.warn('[alert-toast]', text)
	showToast(text)
}

createApp(App).use(router).mount('#app')
