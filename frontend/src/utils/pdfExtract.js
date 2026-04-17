import * as pdfjsLib from 'pdfjs-dist'
import { createWorker } from 'tesseract.js'

// Point the worker at the bundled worker file shipped with pdfjs-dist
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString()

const MIN_TEXT_LENGTH = 80  // chars below this → treat as scanned PDF
const OCR_SCALE = 2.0       // render scale for OCR (higher = better accuracy, slower)
const MAX_OCR_PAGES = 20    // cap to avoid browser OOM

async function ocrImages(files, onProgress) {
  const worker = await createWorker('eng')
  const parts = []
  const total = Math.min(files.length, MAX_OCR_PAGES)
  try {
    for (let i = 0; i < total; i++) {
      const file = files[i]
      const { data } = await worker.recognize(file)
      parts.push(data.text)
      if (onProgress) onProgress(i + 1, total)
    }
  } finally {
    await worker.terminate()
  }
  return parts.join('\n\n')
}

/**
 * Render a single PDF page to an HTMLCanvasElement.
 */
async function renderPageToCanvas(page, scale) {
  const viewport = page.getViewport({ scale })
  const canvas = document.createElement('canvas')
  canvas.width = viewport.width
  canvas.height = viewport.height
  await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise
  return canvas
}

/**
 * OCR all pages of a loaded pdf.js document using tesseract.js.
 * @param {PDFDocumentProxy} pdf
 * @param {function} onProgress  optional callback(pageIndex, totalPages)
 */
async function ocrPdf(pdf, onProgress) {
  const worker = await createWorker('eng')
  const parts = []
  const total = Math.min(pdf.numPages, MAX_OCR_PAGES)
  try {
    for (let i = 1; i <= total; i++) {
      const page = await pdf.getPage(i)
      const canvas = await renderPageToCanvas(page, OCR_SCALE)
      const { data } = await worker.recognize(canvas)
      parts.push(data.text)
      if (onProgress) onProgress(i, total)
    }
  } finally {
    await worker.terminate()
  }
  return parts.join('\n\n')
}

/**
 * Extract text from a PDF File/Blob in the browser.
 * - Uses pdf.js direct text extraction first (fast, works for text-based PDFs).
 * - Falls back to tesseract.js OCR if direct extraction yields too little text.
 *
 * @param {File|Blob} file
 * @param {function}  onProgress  optional callback({ stage: 'ocr', page, total })
 * @returns {Promise<string>}     extracted text (empty string on total failure)
 */
export async function extractTextFromPdf(file, onProgress) {
  try {
    const arrayBuffer = await file.arrayBuffer()
    const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise

    // Step 1: direct text layer extraction (instant for text-based PDFs)
    const parts = []
    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i)
      const content = await page.getTextContent()
      parts.push(content.items.map(item => item.str).join(' '))
    }
    const directText = parts.join('\n\n')

    if (directText.trim().length >= MIN_TEXT_LENGTH) {
      return directText
    }

    // Step 2: OCR fallback (scanned / image-based PDF)
    console.info('[pdfExtract] direct extraction yielded', directText.trim().length,
      'chars – switching to Tesseract OCR')
    return await ocrPdf(pdf, (page, total) => {
      if (onProgress) onProgress({ stage: 'ocr', page, total })
    })
  } catch (e) {
    console.warn('[pdfExtract] extraction failed:', e)
    return ''
  }
}

export async function extractTextFromImages(files, onProgress) {
  try {
    const list = Array.isArray(files) ? files : []
    if (!list.length) return ''
    return await ocrImages(list, (page, total) => {
      if (onProgress) onProgress({ stage: 'ocr', page, total })
    })
  } catch (e) {
    console.warn('[pdfExtract] image OCR failed:', e)
    return ''
  }
}
