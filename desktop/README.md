# 拼豆桌面版打包说明（超详细新手版）

这份说明是给“完全不懂技术”的同学准备的。你只要**按顺序点点点/复制命令**，就能把网页项目打成 Windows 安装包。

---

## 一、这套桌面版是怎么工作的（先理解一句话）

桌面版 = `Electron 外壳` + `前端页面` + `后端 Java 程序`。

- Electron 负责“长得像桌面软件”。
- 前端还是你现在的 Vue 页面。
- 后端还是你现在的 Spring Boot。
- 打包后，双击安装包就能安装使用。

---

## 二、你电脑要先装什么

请先确认这 3 个东西已经安装：

1. **Node.js（建议 20 或 22）**
2. **JDK（Java 17）**
3. **Maven（3.9+）**

不会检查也没关系，打开 PowerShell，分别输入：

`node -v`

`java -version`

`mvn -v`

只要都能显示版本号就可以继续。

---

## 三、第一次准备（只做一次）

在项目根目录 `D:/code/pindou` 打开 PowerShell，依次执行：

### 1) 安装前端依赖

`cd frontend`

`npm install`

### 2) 安装桌面打包依赖

`cd ../desktop`

`npm install`

> 看到下载很多包是正常的。

---

## 四、开始打包（重点）

你现在在 `desktop` 目录下，执行：

`npm run dist`

这个命令会自动做 3 件事：

1. 构建前端（生成 `frontend/dist`）
2. 构建后端 jar（生成 `backend/target/*.jar`）
3. 用 Electron 打包成 Windows 安装程序

---

## 五、打包成功后，去哪里找安装包

打包完成后，到这个目录找：

`desktop/dist`

你会看到一个 `.exe` 安装包（通常带 `Setup` 字样），双击安装即可。

---

## 六、卡在“[1/2] 构建前端...”是不是正常？

短时间（1～5 分钟）是正常的，尤其第一次打包。

如果超过 10 分钟没变化：

1. 先按 `Ctrl + C` 停止。
2. 确认你在 `desktop` 目录，不要在项目根目录执行。
3. 单独测试前端构建：

`cd ../frontend`

`npm.cmd run build`

4. 成功后回到桌面目录再打包：

`cd ../desktop`

`npm.cmd run dist`

说明：你看到的 `Unknown user config ...` 是警告，不会阻止打包。

---

## 七、常见报错（按这个排查）

### 报错 1：`node` 不是内部或外部命令

说明 Node.js 没安装好，重装 Node.js（安装时勾选 PATH）。

### 报错 2：`mvn` 不是内部或外部命令

说明 Maven 没装好，或没加环境变量 PATH。

### 报错 3：`java` 找不到

说明 JDK 没装好。请安装 Java 17，并确认 `java -version` 有输出。

### 报错 4：端口冲突（58080 或 58081）

可能是你机器上有别的软件占了端口。可在 `desktop/main.cjs` 里改这两行：

- `BACKEND_PORT`
- `DESKTOP_PORT`

改成别的数字后重新 `npm run dist`。

---

## 八、安装后若打不开总览的一键自检

如果你双击安装好的桌面程序后，看到“加载总览失败”，请按这 5 步排查（从上到下做就行）：

### 第 1 步：先彻底退出旧程序

- 右下角托盘里如果有“拼豆助手”，先退出
- 任务管理器里把 `拼豆助手.exe`、`java.exe` 结束掉

### 第 2 步：确认你安装的是“最新打包”

- 重新运行最新的安装包：`desktop/dist/拼豆助手 Setup 0.1.0.exe`
- 建议先卸载旧版，再安装新版

### 第 3 步：用开发模式快速验证（最稳）

在项目目录依次执行：

`cd desktop`

`npm.cmd run start`

如果这个模式能打开总览，说明代码没问题，通常是旧安装残留导致。

### 第 4 步：检查端口是否被占用（可选）

在 PowerShell 执行：

`netstat -ano | findstr 58080`

`netstat -ano | findstr 58081`

如果有长期占用，重启电脑后再试通常最省心。

### 第 5 步：最后一招（重新打包再安装）

`cd desktop`

`npm.cmd run dist`

然后重新安装新生成的安装包。

---

## 九、如何加应用图标（超详细）

你只需要做 4 步：

### 1) 准备图标文件

- 推荐尺寸：`256x256` 或 `512x512`
- 格式：先准备 `png`，再转换成 `ico`
- 文件名建议：`app.ico`

### 2) 把图标放到目录

把图标放到：`desktop/build/app.ico`

如果 `build` 文件夹不存在，就自己新建一个。

### 3) 修改打包配置

打开 [desktop/package.json](desktop/package.json)，在 `build.win` 下加上 `icon`：

```json
"win": {
	"icon": "build/app.ico",
	"target": [
		{
			"target": "nsis",
			"arch": ["x64"]
		}
	]
}
```

### 4) 重新打包

`cd desktop`

`npm.cmd run dist`

打包完成后，安装程序和安装后的桌面快捷方式都会显示你的新图标。

---

## 十、开发时快速预览（可选）

如果你只是想边改边看桌面壳效果：

1. 先启动后端（一个终端）

`cd backend`

`mvn spring-boot:run`

2. 再启动前端（第二个终端）

`cd frontend`

`npm run dev`

3. 再启动 Electron（第三个终端）

`cd desktop`

`npm run dev`

---

## 十一、你现在最该做什么（一步一步）

1. 打开 PowerShell 到 `D:/code/pindou/desktop`
2. 执行 `npm install`
3. 执行 `npm run dist`
4. 到 `desktop/dist` 找 `.exe` 安装包

就这四步。

---

如果你愿意，我下一步可以继续帮你做两件“更适合给别人安装”的优化：

1. 加应用图标（桌面图标更美观）
2. 把 Java 运行时一起打包（别人电脑没装 Java 也能用）
