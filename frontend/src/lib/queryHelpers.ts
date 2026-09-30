import type { QueryClient } from '@tanstack/react-query'
import type { Post } from './api/types'

function isCursorPage(value: unknown): value is { items: unknown[] } {
  return typeof value === 'object' && value !== null && Array.isArray((value as { items?: unknown }).items)
}

function isInfiniteData(value: unknown): value is { pages: unknown[] } {
  return typeof value === 'object' && value !== null && Array.isArray((value as { pages?: unknown }).pages)
}

function isPost(value: unknown): value is Post {
  return typeof value === 'object' && value !== null && 'likeCount' in value && 'hashtags' in value
}

/**
 * A post's like/comment state is cached independently in N places at once (home feed, explore,
 * a profile grid, the hashtag grid, a single-post detail view) — this patches all of them via a
 * predicate over every query's cached shape rather than a fixed list of query keys, so a like/
 * comment mutation is instantly reflected everywhere the post happens to be cached. Deliberately
 * does not force a refetch afterwards (that would reshuffle an in-progress scroll).
 */
export function patchPostInAllCaches(queryClient: QueryClient, postId: number, patch: Partial<Post>) {
  const applyToPost = (post: Post): Post => (post.id === postId ? { ...post, ...patch } : post)

  queryClient.setQueriesData({ predicate: () => true }, (data: unknown) => {
    if (isInfiniteData(data)) {
      return {
        ...data,
        pages: data.pages.map((page) =>
          isCursorPage(page) ? { ...page, items: (page.items as Post[]).map(applyToPost) } : page,
        ),
      }
    }
    if (isCursorPage(data)) {
      return { ...data, items: (data.items as Post[]).map(applyToPost) }
    }
    if (isPost(data)) {
      return applyToPost(data)
    }
    return data
  })
}
