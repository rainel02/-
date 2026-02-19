const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..', '..')
const desktopDir = path.resolve(__dirname, '..')

const frontendDist = path.join(root, 'frontend', 'dist')
const backendTarget = path.join(root, 'backend', 'target')
const outputDir = path.join(desktopDir, 'app-resources')
const outputFrontend = path.join(outputDir, 'frontend-dist')
const outputBackend = path.join(outputDir, 'backend')
const outputJar = path.join(outputBackend, 'backend.jar')

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true })
}

function cleanDir(dir) {
  if (fs.existsSync(dir)) {
    fs.rmSync(dir, { recursive: true, force: true })
  }
}

function copyDir(src, dest) {
  fs.cpSync(src, dest, { recursive: true })
}

function findBackendJar() {
  if (!fs.existsSync(backendTarget)) return null
  const files = fs.readdirSync(backendTarget)
  const jars = files
    .filter(name => name.endsWith('.jar'))
    .filter(name => !name.endsWith('.original'))
    .sort((a, b) => (a.includes('SNAPSHOT') ? 1 : 0) - (b.includes('SNAPSHOT') ? 1 : 0))

  if (jars.length === 0) return null
  return path.join(backendTarget, jars[0])
}

if (!fs.existsSync(frontendDist)) {
  console.error('❌ 未找到 frontend/dist，请先执行前端构建')
  process.exit(1)
}

const jarPath = findBackendJar()
if (!jarPath) {
  console.error('❌ 未找到 backend/target/*.jar，请先执行后端构建')
  process.exit(1)
}

cleanDir(outputDir)
ensureDir(outputDir)
ensureDir(outputBackend)

copyDir(frontendDist, outputFrontend)
fs.copyFileSync(jarPath, outputJar)

console.log('✅ 已准备打包资源：')
console.log(`- 前端: ${outputFrontend}`)
console.log(`- 后端: ${outputJar}`)
