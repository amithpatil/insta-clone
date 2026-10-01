import { apiFetch } from '../client'
import type { FollowStatusResponse } from '../types'

export function follow(username: string) {
  return apiFetch<FollowStatusResponse>(`/users/${encodeURIComponent(username)}/follow`, { method: 'POST' })
}

export function unfollow(username: string) {
  return apiFetch<void>(`/users/${encodeURIComponent(username)}/follow`, { method: 'DELETE' })
}

export function acceptFollowRequest(username: string) {
  return apiFetch<FollowStatusResponse>(`/users/${encodeURIComponent(username)}/follow/accept`, { method: 'POST' })
}

export function rejectFollowRequest(username: string) {
  return apiFetch<void>(`/users/${encodeURIComponent(username)}/follow/reject`, { method: 'DELETE' })
}
