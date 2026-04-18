export function resolveGatewayProxyTarget(env) {
  return env.VITE_GATEWAY_PROXY_TARGET || 'http://localhost:8087'
}

export function createApiProxy(env) {
  return {
    '/api': {
      target: resolveGatewayProxyTarget(env),
      changeOrigin: true,
    },
  }
}
