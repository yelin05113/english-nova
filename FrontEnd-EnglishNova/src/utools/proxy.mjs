export function resolveGatewayProxyTarget(env) {
  return env.VITE_GATEWAY_PROXY_TARGET || 'http://localhost:8087'
}

export function createApiProxy(env) {
  const target = resolveGatewayProxyTarget(env)
  const proxy = {}
  for (const path of [
    '^/auth/(login|register|me|profile|profile/avatar)$',
    '^/upload/images/',
    '^/system/',
    '^/study/',
    '^/search/',
    '^/public-wordbooks',
    '^/imports/(presets|files)$',
    '^/wordbooks',
    '^/quiz/sessions',
  ]) {
    proxy[path] = { target, changeOrigin: true }
  }
  return proxy
}
