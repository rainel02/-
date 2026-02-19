const path = require('path')
const { spawnSync } = require('child_process')

const root = path.resolve(__dirname, '..', '..')
const frontendDir = path.join(root, 'frontend')
const backendDir = path.join(root, 'backend')

function bin(name) {
  return name
}

function run(command, args, cwd) {
  const pretty = `${command} ${args.join(' ')}`
  console.log(`[run] ${pretty} (cwd: ${cwd})`)

  const result = spawnSync(command, args, {
    cwd,
    stdio: 'inherit',
    shell: true
  })

  if (result.error) {
    console.error(`[error] 执行失败: ${pretty}`)
    console.error(result.error)
    process.exit(1)
  }

  if (result.status !== 0) {
    console.error(`[error] 命令退出码: ${result.status}`)
    process.exit(result.status || 1)
  }
}

console.log('\n[1/2] 构建前端...')
run(bin('npm'), ['run', 'build'], frontendDir)

console.log('\n[2/2] 构建后端...')
run(bin('mvn'), ['clean', 'package', '-DskipTests'], backendDir)

console.log('\n✅ 前后端构建完成')
