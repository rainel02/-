const { app, BrowserWindow, dialog } = require('electron')
const path = require('path')
const { spawn } = require('child_process')
const http = require('http')
const fs = require('fs')
const express = require('express')

const BACKEND_PORT = Number(process.env.PINDOU_BACKEND_PORT || 58080)
const API_HEALTH_PATH = '/api/beads/tags'
const FRONTEND_PORT_BASE = Number(process.env.PINDOU_FRONTEND_PORT || 59081)

let backendProc = null
let mainWindow = null
let backendPort = BACKEND_PORT
let frontendServer = null
let frontendPort = FRONTEND_PORT_BASE

function normalizePathForJdbc(filePath) {
  return filePath.replace(/\\/g, '/')
}

function ensureDir(dirPath) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true })
  }
}

function configureStoragePaths() {
  const folderName = app.isPackaged ? 'PindouDesktop' : 'PindouDesktopDev'
  const base = path.join(app.getPath('appData'), folderName)
  const cacheDir = path.join(base, 'Cache')
  ensureDir(base)
  ensureDir(cacheDir)
  app.setPath('userData', base)
  app.setPath('sessionData', base)
  app.setPath('cache', cacheDir)
}

function isDev() {
  return !app.isPackaged
}

function getProjectRoot() {
  return path.resolve(__dirname, '..')
}

function getBackendJarPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'app-resources', 'backend', 'backend.jar')
  }
  const targetDir = path.join(getProjectRoot(), 'backend', 'target')
  const devCandidates = [
    path.join(targetDir, 'backend.jar'),
    path.join(targetDir, 'backend-0.0.1-SNAPSHOT.jar')
  ]
  const found = devCandidates.find(filePath => fs.existsSync(filePath))
  return found || devCandidates[0]
}

function getFrontendDistPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'app-resources', 'frontend-dist')
  }
  return path.join(getProjectRoot(), 'frontend', 'dist')
}

function getBackendDataDir() {
  const dataDir = path.join(app.getPath('userData'), 'data')
  ensureDir(dataDir)
  return dataDir
}

function getPersistentDbBasePath() {
  return path.join(getBackendDataDir(), 'pindou-db')
}

function hasH2DbFiles(dbBasePath) {
  const candidates = [
    `${dbBasePath}.mv.db`,
    `${dbBasePath}.trace.db`,
    `${dbBasePath}.lock.db`
  ]
  return candidates.some(filePath => fs.existsSync(filePath))
}

function copyDbFiles(fromDataDir, toDataDir) {
  if (!fs.existsSync(fromDataDir)) return 0
  const names = fs.readdirSync(fromDataDir)
  let copiedCount = 0
  for (const name of names) {
    if (!name.startsWith('pindou-db')) continue
    const src = path.join(fromDataDir, name)
    const dest = path.join(toDataDir, name)
    if (!fs.statSync(src).isFile()) continue
    fs.copyFileSync(src, dest)
    copiedCount += 1
  }
  return copiedCount
}

function migrateLegacyDatabaseIfNeeded() {
  const targetBasePath = getPersistentDbBasePath()
  if (hasH2DbFiles(targetBasePath)) {
    return
  }

  const candidateDataDirs = []
  const cwdData = path.join(process.cwd(), 'data')
  candidateDataDirs.push(cwdData)

  const execData = path.join(path.dirname(process.execPath), 'data')
  candidateDataDirs.push(execData)

  const projectData = path.join(getProjectRoot(), 'data')
  candidateDataDirs.push(projectData)

  const targetDataDir = getBackendDataDir()
  for (const candidateDir of candidateDataDirs) {
    try {
      if (!fs.existsSync(candidateDir)) continue
      if (path.resolve(candidateDir) === path.resolve(targetDataDir)) continue
      const candidateBasePath = path.join(candidateDir, 'pindou-db')
      if (!hasH2DbFiles(candidateBasePath)) continue

      const copied = copyDbFiles(candidateDir, targetDataDir)
      if (copied > 0) {
        console.log(`[backend] 已迁移历史数据：${candidateDir} -> ${targetDataDir}`)
      }
      return
    } catch (error) {
      console.warn(`[backend] 历史数据迁移失败，已跳过 ${candidateDir}: ${String(error?.message || error)}`)
    }
  }
}

function getBackendDatasourceUrl() {
  const dbPath = normalizePathForJdbc(getPersistentDbBasePath())
  return `jdbc:h2:file:${dbPath};AUTO_SERVER=TRUE`
}

function wait(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function checkUrl(url) {
  return new Promise(resolve => {
    const req = http.get(url, res => {
      res.resume()
      resolve(res.statusCode && res.statusCode < 500)
    })
    req.on('error', () => resolve(false))
    req.setTimeout(2000, () => {
      req.destroy()
      resolve(false)
    })
  })
}

async function waitForBackendReady(port, retrySeconds = 60) {
  const url = `http://127.0.0.1:${port}${API_HEALTH_PATH}`
  for (let i = 0; i < retrySeconds; i += 1) {
    const ok = await checkUrl(url)
    if (ok) return
    await wait(1000)
  }
  throw new Error(`后端启动超时（${retrySeconds} 秒）`)
}

function startBackendJar(port) {
  const jarPath = getBackendJarPath()
  if (!fs.existsSync(jarPath)) {
    throw new Error(`未找到后端 jar 文件：${jarPath}`)
  }

  const javaCmd = process.env.PINDOU_JAVA_CMD || 'java'
  const args = ['-jar', jarPath, `--server.port=${port}`, `--spring.datasource.url=${getBackendDatasourceUrl()}`]

  console.log(`[backend] data dir: ${getBackendDataDir()}`)

  backendProc = spawn(javaCmd, args, {
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true
  })

  backendProc.stdout?.on('data', chunk => {
    const text = chunk.toString().trim()
    if (text) {
      console.log(`[backend] ${text}`)
    }
  })

  backendProc.stderr?.on('data', chunk => {
    const text = chunk.toString().trim()
    if (text) {
      console.error(`[backend] ${text}`)
    }
  })

  backendProc.on('exit', code => {
    console.log(`[backend] exited with code ${code}`)
    backendProc = null
  })

  backendProc.on('error', error => {
    console.error('[backend] failed to start', error)
  })
}

function stopBackendJar() {
  if (!backendProc) return
  try {
    backendProc.kill()
  } catch (_) {
  }
}

async function startFrontendHost() {
  const frontendDist = getFrontendDistPath()
  if (!fs.existsSync(frontendDist)) {
    throw new Error(`未找到前端目录：${frontendDist}，请先执行 npm run dist`)
  }

  const web = express()
  web.use(express.static(frontendDist))
  web.get('*', (_req, res) => {
    res.sendFile(path.join(frontendDist, 'index.html'))
  })

  let tryPort = FRONTEND_PORT_BASE
  while (tryPort < FRONTEND_PORT_BASE + 20) {
    try {
      await new Promise((resolve, reject) => {
        const server = web.listen(tryPort, '127.0.0.1', () => resolve())
        server.on('error', reject)
        frontendServer = server
      })
      frontendPort = tryPort
      console.log(`[frontend] static host started at http://127.0.0.1:${frontendPort}`)
      return
    } catch (error) {
      if (error && error.code === 'EADDRINUSE') {
        tryPort += 1
        continue
      }
      throw error
    }
  }

  throw new Error(`前端端口占用严重，无法监听 ${FRONTEND_PORT_BASE}~${FRONTEND_PORT_BASE + 19}`)
}

function createWindow() {
  const devUrl = process.env.ELECTRON_DEV_URL
  const injectedApiBase = `http://127.0.0.1:${backendPort}/api`

  mainWindow = new BrowserWindow({
    width: 1366,
    height: 900,
    minWidth: 1200,
    minHeight: 760,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      additionalArguments: [`--pindou-api-base=${injectedApiBase}`]
    }
  })

  if (devUrl) {
    mainWindow.loadURL(devUrl)
    return
  }
  mainWindow.loadURL(`http://127.0.0.1:${frontendPort}`)
}

function stopFrontendHost() {
  if (!frontendServer) return
  try {
    frontendServer.close()
  } catch (_) {
  }
  frontendServer = null
}

const singleInstanceLock = app.requestSingleInstanceLock()
if (!singleInstanceLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return
    if (mainWindow.isMinimized()) {
      mainWindow.restore()
    }
    mainWindow.focus()
  })
}

configureStoragePaths()

async function detectAvailableBackendPort() {
  const candidates = [BACKEND_PORT, 8080]
  for (const port of candidates) {
    const ok = await checkUrl(`http://127.0.0.1:${port}${API_HEALTH_PATH}`)
    if (ok) return port
  }
  return null
}

async function bootstrap() {
  try {
    if (process.env.ELECTRON_DEV_URL) {
      createWindow()
      return
    }

    migrateLegacyDatabaseIfNeeded()

    const existingPort = await detectAvailableBackendPort()
    if (existingPort) {
      backendPort = existingPort
    } else {
      backendPort = BACKEND_PORT
      startBackendJar(backendPort)
      await waitForBackendReady(backendPort, 90)
    }

    await startFrontendHost()
    createWindow()
  } catch (error) {
    console.error(error)
    dialog.showErrorBox('启动失败', String(error?.message || error))
    app.quit()
  }
}

app.whenReady().then(bootstrap)

app.on('before-quit', () => {
  stopFrontendHost()
  stopBackendJar()
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
