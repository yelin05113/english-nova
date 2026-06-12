import { createHmac } from 'node:crypto'

export function resolveGatewayProxyTarget(env) {
  return env.VITE_GATEWAY_PROXY_TARGET || 'http://localhost:8087'
}

export function resolveQuizProxyTarget(env) {
  return env.VITE_QUIZ_PROXY_TARGET || 'http://localhost:8086'
}

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

function signInternalAuth(secret, userId, username, timestamp) {
  const payload = `${userId}\n${username}\n${timestamp}`
  return createHmac('sha256', secret).update(payload, 'utf8').digest('base64url')
}

function applyInternalAuthHeaders(proxyOptions, env) {
  const internalAuthSecret =
    env.VITE_INTERNAL_AUTH_SECRET ||
    env.INTERNAL_AUTH_SECRET ||
    'english-nova-internal-auth-secret-change-me'

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

        const timestamp = Math.floor(Date.now() / 1000).toString()
        const signature = signInternalAuth(internalAuthSecret, String(userId), String(username), timestamp)

        proxyReq.setHeader('X-Auth-User-Id', String(userId))
        proxyReq.setHeader('X-Auth-Username', String(username))
        proxyReq.setHeader('X-Auth-Timestamp', timestamp)
        proxyReq.setHeader('X-Auth-Signature', signature)
      })
    },
  }
}

export function createApiProxy(env) {
  const gatewayTarget = resolveGatewayProxyTarget(env)
  const quizTarget = resolveQuizProxyTarget(env)
  const proxy = {}

  for (const path of [
    '/auth/login',
    '/auth/register',
    '/auth/me',
    '/auth/profile',
    '/auth/preferences/',
    '/upload/images/',
    '/system/',
    '/study/',
    '/search/',
    '/public-wordbooks',
    '/imports/presets',
    '/imports/files',
  ]) {
    proxy[path] = { target: gatewayTarget, changeOrigin: true }
  }

  for (const path of ['/wordbooks', '/word-notebooks', '/quiz/sessions']) {
    proxy[path] = applyInternalAuthHeaders({ target: quizTarget, changeOrigin: true }, env)
  }

  return proxy
}
