function decodeJwtPayload(token) {
  const parts = token.split('.')
  if (parts.length < 2) {
    return null
  }

  try {
    const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    return JSON.parse(Buffer.from(padded, 'base64').toString('utf8'))
  } catch {
    return null
  }
}

function applyDevAuthHeaders(proxyOptions) {
  return {
    ...proxyOptions,
    configure(proxyServer) {
      proxyServer.on('proxyReq', (proxyReq, req) => {
        const authHeader = req.headers.authorization
        if (!authHeader || !authHeader.startsWith('Bearer ')) {
          return
        }

        const payload = decodeJwtPayload(authHeader.slice(7).trim())
        const userId = payload?.sub
        const username = payload?.username
        if (!userId || !username) {
          return
        }

        proxyReq.setHeader('X-Auth-User-Id', String(userId))
        proxyReq.setHeader('X-Auth-Username', String(username))
      })
    },
  }
}

export function resolveGatewayProxyTarget(env) {
  return env.VITE_GATEWAY_PROXY_TARGET || 'http://localhost:8087'
}

export function resolveQuizProxyTarget(env) {
  return env.VITE_QUIZ_PROXY_TARGET || 'http://localhost:8086'
}

export function createApiProxy(env) {
  const gatewayTarget = resolveGatewayProxyTarget(env)
  const quizTarget = resolveQuizProxyTarget(env)
  const proxy = {}

  for (const path of [
    '^/auth/(login|register|me|profile|profile/avatar)$',
    '^/auth/preferences/',
    '^/upload/images/',
    '^/system/',
    '^/study/',
    '^/search/',
    '^/public-wordbooks',
    '^/imports/(presets|files)$',
  ]) {
    proxy[path] = { target: gatewayTarget, changeOrigin: true }
  }

  for (const path of ['^/wordbooks', '^/word-notebooks', '^/quiz/sessions']) {
    proxy[path] = applyDevAuthHeaders({ target: quizTarget, changeOrigin: true })
  }

  return proxy
}
