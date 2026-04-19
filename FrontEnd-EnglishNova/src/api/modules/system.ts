import { apiFetch } from '../client'

export interface SystemModule {
  name: string
  responsibility: string
  status: string
}

export interface SystemOverview {
  productName: string
  theme: string
  supportedPlatforms: string[]
  modules: SystemModule[]
  deliveryPhases: string[]
}

async function getOverview() {
  return apiFetch<SystemOverview>('/api/system/overview')
}

export const systemApi = {
  getOverview,
}
