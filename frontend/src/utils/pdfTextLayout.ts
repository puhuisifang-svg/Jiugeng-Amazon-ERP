export type PdfText = { text: string; fontSize: number; color: string }
export const PDF_TEXT_FONT = '"PingFang SC", "Microsoft YaHei", sans-serif'
// Both the editing canvas and exported overlay use PDF points and this exact baseline.
export function measurePdfText(ctx: CanvasRenderingContext2D, layer: PdfText) {
  ctx.font = `${layer.fontSize}px ${PDF_TEXT_FONT}`
  const lines = layer.text.split('\n')
  return { width: Math.max(1, ...lines.map(line => ctx.measureText(line).width)) + 2, height: Math.max(1, lines.length * layer.fontSize * 1.3) + 2 }
}
export function drawPdfText(ctx: CanvasRenderingContext2D, layer: PdfText, x = 0, y = 0) {
  ctx.font = `${layer.fontSize}px ${PDF_TEXT_FONT}`
  ctx.textBaseline = 'top'; ctx.fillStyle = layer.color
  layer.text.split('\n').forEach((line, index) => ctx.fillText(line, x, y + index * layer.fontSize * 1.3 + layer.fontSize * .15))
}
