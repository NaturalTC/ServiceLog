// Shrinks a photo on the phone before uploading: a 12-megapixel camera photo becomes a
// ~2000px JPEG of a few hundred KB, which matters on one bar of signal in a basement.
// Drawing it upright onto a canvas also drops the camera's hidden metadata (GPS location);
// the server re-encodes again as its own guarantee.

const MAX_SIZE = 2000
const QUALITY = 0.85

export async function shrinkPhoto(file: File): Promise<Blob> {
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  } catch {
    // A format the browser can't draw: let the server decide
    return file
  }
  const ratio = Math.min(1, MAX_SIZE / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * ratio)
  canvas.height = Math.round(bitmap.height * ratio)
  const context = canvas.getContext('2d')
  if (!context) return file
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', QUALITY))
  return blob ?? file
}
