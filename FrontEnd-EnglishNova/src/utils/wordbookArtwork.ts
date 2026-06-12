export type WordbookArtworkKind = 'imported' | 'public'

const artworkModules = import.meta.glob('../img/*.{png,jpg,jpeg,webp,avif,svg}', {
  eager: true,
  import: 'default',
}) as Record<string, string>

function normalizeWordbookName(name: string) {
  return name
    .trim()
    .toLowerCase()
    .replace(/[\s_-]+/g, '')
    .replace(/[()[\]{}，。、“”‘’"'!?？！\\|,.]/g, '')
}

const artworkByName = Object.fromEntries(
  Object.entries(artworkModules).map(([path, src]) => {
    const filename = path.split('/').pop() ?? path
    const basename = filename.replace(/\.[^.]+$/, '')
    return [normalizeWordbookName(basename), src]
  }),
) as Record<string, string>

const sharedArtworkAliases = {
  '\u521d\u4e2d': 'junior',
  junior: 'junior',
  '\u9ad8\u4e2d': 'high',
  high: 'high',
  '\u82f1\u8bed\u56db\u7ea7': 'cet4',
  '\u56db\u7ea7': 'cet4',
  cet4: 'cet4',
  '\u82f1\u8bed\u516d\u7ea7': 'cet6',
  '\u516d\u7ea7': 'cet6',
  cet6: 'cet6',
  '\u8003\u7814\u82f1\u8bed': 'kyenglish',
  '\u8003\u7814': 'kyenglish',
  kyenglish: 'kyenglish',
  '\u6258\u798f': 'toefl',
  toefl: 'toefl',
  sat: 'sat',
  SAT: 'sat',
} as const

const artworkAliasMap: Record<WordbookArtworkKind, Record<string, string>> = {
  imported: { ...sharedArtworkAliases },
  public: { ...sharedArtworkAliases },
}

export function getWordbookArtwork(name: string, kind: WordbookArtworkKind) {
  if (!name) return null
  const normalizedName = normalizeWordbookName(name)

  if (artworkByName[normalizedName]) {
    return artworkByName[normalizedName]
  }

  for (const [alias, artworkKey] of Object.entries(artworkAliasMap[kind])) {
    const normalizedAlias = normalizeWordbookName(alias)
    if (normalizedAlias === normalizedName || normalizedName.includes(normalizedAlias)) {
      return artworkByName[normalizeWordbookName(artworkKey)] ?? null
    }
  }

  return null
}
