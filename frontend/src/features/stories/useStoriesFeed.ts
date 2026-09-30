import { useQuery } from '@tanstack/react-query'
import * as storiesApi from '@/lib/api/endpoints/stories'
import type { Story, UserSummary } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'

export interface AuthorStories {
  author: UserSummary
  stories: Story[]
}

/** Groups the flat /stories/feed list into one entry per author, in first-seen order — the tray shows one ring per author, and the viewer steps through that author's stories in sequence. */
export function useStoriesFeed() {
  const query = useQuery({
    queryKey: queryKeys.storiesFeed(),
    queryFn: () => storiesApi.getStoriesFeed(),
  })

  const grouped: AuthorStories[] = []
  const indexByAuthor = new Map<number, number>()
  for (const story of query.data?.items ?? []) {
    const existingIndex = indexByAuthor.get(story.author.id)
    if (existingIndex === undefined) {
      indexByAuthor.set(story.author.id, grouped.length)
      grouped.push({ author: story.author, stories: [story] })
    } else {
      grouped[existingIndex].stories.push(story)
    }
  }

  return { ...query, groups: grouped }
}
