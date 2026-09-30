import { apiFetch, buildQuery } from '../client'
import type { CursorPage, UserProfile, UserSummary } from '../types'

export interface UpdateProfileRequest {
  fullName?: string
  bio?: string
  profilePictureUrl?: string
  isPrivate?: boolean
}

export function getProfile(username: string) {
  return apiFetch<UserProfile>(`/users/${encodeURIComponent(username)}`)
}

export function updateMyProfile(body: UpdateProfileRequest) {
  return apiFetch<UserProfile>('/users/me', { method: 'PATCH', body })
}

export function getFollowers(username: string, cursor?: string, limit?: number) {
  return apiFetch<CursorPage<UserSummary>>(
    `/users/${encodeURIComponent(username)}/followers${buildQuery({ cursor, limit })}`,
  )
}

export function getFollowing(username: string, cursor?: string, limit?: number) {
  return apiFetch<CursorPage<UserSummary>>(
    `/users/${encodeURIComponent(username)}/following${buildQuery({ cursor, limit })}`,
  )
}
