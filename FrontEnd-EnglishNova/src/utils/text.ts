export function formatMultilineText(value?: string | null) {
  if (!value) {
    return ''
  }

  return value.replace(/\\r\\n/g, '\n').replace(/\\n/g, '\n').replace(/\r\n/g, '\n')
}

export function formatMeaningText(value?: string | null) {
  const normalized = formatMultilineText(value)
  if (!normalized) {
    return ''
  }

  return normalized
    .replace(/\s+(?=(n|adj|adv|v|vi|vt|phr|prep|pron|conj|int|num|art|aux)\.)/gi, '\n')
    .replace(/\n{2,}/g, '\n')
}
