import { useMutation, useQueryClient } from '@tanstack/react-query'
import * as followApi from '@/lib/api/endpoints/follow'
import type { UserProfile, ViewerRelationship } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'

/**
 * Unlike posts, a UserProfile's follow state isn't duplicated across list caches — UserSummary
 * (the shape embedded in feeds/search/comments) carries no follow-state field at all, so the only
 * cache entry that needs patching is this profile's own queryKeys.userProfile(username).
 */
export function useFollowMutation(username: string, profile: UserProfile) {
  const queryClient = useQueryClient()
  const key = queryKeys.userProfile(username)

  const patch = (relationship: ViewerRelationship, followerDelta: number) => {
    queryClient.setQueryData<UserProfile>(key, (current) =>
      current
        ? { ...current, viewerRelationship: relationship, followerCount: current.followerCount + followerDelta }
        : current,
    )
  }

  const follow = useMutation({
    mutationFn: () => followApi.follow(username),
    onMutate: () => {
      // A private account's follow request is PENDING (not FOLLOWING) until approved — optimistically
      // showing FOLLOWING for a private account would lie about state until the real response lands.
      const optimisticRelationship: ViewerRelationship = profile.isPrivate ? 'REQUESTED' : 'FOLLOWING'
      patch(optimisticRelationship, profile.isPrivate ? 0 : 1)
      return { previous: profile.viewerRelationship, previousCount: profile.followerCount }
    },
    onSuccess: (result) => {
      // Reconcile the optimistic guess with the server's authoritative status (a private account
      // could in theory auto-accept or the reverse — trust the response, not the optimistic value).
      patch(result.status === 'ACCEPTED' ? 'FOLLOWING' : 'REQUESTED', 0)
      // A newly-followed (or requested) user should drop out of Suggested Accounts elsewhere in
      // the app, not just update this profile's own cache entry.
      queryClient.invalidateQueries({ queryKey: queryKeys.suggestions() })
    },
    onError: (_err, _vars, context) => {
      if (context) patch(context.previous, 0)
    },
  })

  const unfollow = useMutation({
    mutationFn: () => followApi.unfollow(username),
    onMutate: () => {
      patch('NOT_FOLLOWING', -1)
      return { previous: profile.viewerRelationship }
    },
    onError: (_err, _vars, context) => {
      if (context) patch(context.previous, 1)
    },
  })

  return { follow, unfollow }
}
