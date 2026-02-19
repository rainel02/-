import { createWorker, PSM } from 'tesseract.js'
import type { ColorRequirement } from '@/types'

function normalizeColorCode(code: string): string {
  return code.toUpperCase().trim().replace(/^([A-Z]+)0+(?=[1-9])/, '$1')
}

function parseOcrText(rawText: string): ColorRequirement[] {
  const cleanText = rawText
    .replace(/[\r\n]+/g, ' ')
    .replace(/[()]/g, ' ')
    .replace(/[x×]\s*(\d+)/gi, ' $1')
    .trim()

  const tokens = cleanText.split(/[\s,]+/).filter(Boolean)
  const results: ColorRequirement[] = []

  for (let index = 0; index < tokens.length; index += 1) {
    let token = tokens[index]

    if (/^[A-Z]/i.test(token)) {
      const firstChar = token[0]
      const rest = token
        .slice(1)
        .replace(/O/g, '0')
        .replace(/o/g, '0')
        .replace(/l/g, '1')
        .replace(/I/g, '1')
      token = firstChar + rest
    }

    if (!/^[A-Z]{1,2}\d{1,4}$/i.test(token) || token.length > 6) {
      continue
    }

    const code = normalizeColorCode(token)
    let quantity = 1

    if (index + 1 < tokens.length && /^\d+$/.test(tokens[index + 1])) {
      quantity = Number.parseInt(tokens[index + 1], 10)
      index += 1
    }

    if (quantity > 0) {
      results.push({ code, quantity })
    }
  }

  return results
}

function mergeResults(primary: ColorRequirement[], secondary: ColorRequirement[]): ColorRequirement[] {
  const merged = new Map<string, number>()

  for (const row of [...primary, ...secondary]) {
    if (!row.code) {
      continue
    }
    const exists = merged.get(row.code)
    if (!exists) {
      merged.set(row.code, row.quantity)
      continue
    }
    if (exists <= 1 && row.quantity > 1) {
      merged.set(row.code, row.quantity)
      continue
    }
    if (row.quantity > exists) {
      merged.set(row.code, row.quantity)
    }
  }

  return Array.from(merged.entries())
    .map(([code, quantity]) => ({ code, quantity }))
    .sort((left, right) => left.code.localeCompare(right.code, 'en'))
}

async function dataUrlToImage(dataUrl: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('图像加载失败'))
    image.src = dataUrl
  })
}

async function preprocess(dataUrl: string): Promise<{ normal: string; inverted: string }> {
  const image = await dataUrlToImage(dataUrl)
  const scale = 2.5
  const width = Math.max(1, Math.floor(image.width * scale))
  const height = Math.max(1, Math.floor(image.height * scale))

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height

  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('无法创建图像处理上下文')
  }

  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(image, 0, 0, width, height)

  const imageData = context.getImageData(0, 0, width, height)
  const data = imageData.data

  let min = 255
  let max = 0

  for (let index = 0; index < data.length; index += 4) {
    const gray = 0.2126 * data[index] + 0.7152 * data[index + 1] + 0.0722 * data[index + 2]
    data[index] = gray
    data[index + 1] = gray
    data[index + 2] = gray
    if (gray < min) {
      min = gray
    }
    if (gray > max) {
      max = gray
    }
  }

  if (max > min) {
    for (let index = 0; index < data.length; index += 4) {
      const normalized = ((data[index] - min) * 255) / (max - min)
      data[index] = normalized
      data[index + 1] = normalized
      data[index + 2] = normalized
    }
  }

  context.putImageData(imageData, 0, 0)
  const normal = canvas.toDataURL('image/png')

  for (let index = 0; index < data.length; index += 4) {
    const inverted = 255 - data[index]
    data[index] = inverted
    data[index + 1] = inverted
    data[index + 2] = inverted
  }

  context.putImageData(imageData, 0, 0)
  const inverted = canvas.toDataURL('image/png')

  return { normal, inverted }
}

export async function extractColorRequirementsFromImage(dataUrl: string): Promise<ColorRequirement[]> {
  if (!dataUrl) {
    return []
  }

  const { normal, inverted } = await preprocess(dataUrl)
  const worker = await createWorker('eng')

  try {
    await worker.setParameters({
      tessedit_char_whitelist: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789()x×',
      tessedit_pageseg_mode: PSM.SPARSE_TEXT
    })

    const normalResult = await worker.recognize(normal)
    const invertedResult = await worker.recognize(inverted)

    const parsedNormal = parseOcrText(normalResult.data.text)
    const parsedInverted = parseOcrText(invertedResult.data.text)
    return mergeResults(parsedNormal, parsedInverted)
  } finally {
    await worker.terminate()
  }
}
