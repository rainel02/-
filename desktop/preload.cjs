const { contextBridge } = require('electron')

function getArgValue(prefix) {
  const arg = process.argv.find(item => item.startsWith(prefix))
  if (!arg) return ''
  return arg.slice(prefix.length)
}

const apiBase = getArgValue('--pindou-api-base=')

contextBridge.exposeInMainWorld('pindouDesktop', {
  version: '0.1.0',
  apiBase
})
