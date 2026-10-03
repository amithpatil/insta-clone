import type { Client, IMessage } from '@stomp/stompjs'
import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import type { CursorPage, Message, Notification } from '@/lib/api/types'
import { queryKeys } from '@/lib/queryKeys'

function prependToFirstPage<T>(
  queryClient: QueryClient,
  key: readonly unknown[],
  item: T,
): void {
  queryClient.setQueryData<InfiniteData<CursorPage<T>>>(key, (data) => {
    if (!data || data.pages.length === 0) return data
    const [firstPage, ...rest] = data.pages
    return { ...data, pages: [{ ...firstPage, items: [item, ...firstPage.items] }, ...rest] }
  })
}

/** No separate WS store — incoming pushes are written straight into the Query cache, the single source of truth for both REST-fetched and WS-pushed data. */
export function registerHandlers(stomp: Client, queryClient: QueryClient) {
  stomp.subscribe('/user/queue/notifications', (frame: IMessage) => {
    const notification = JSON.parse(frame.body) as Notification
    prependToFirstPage(queryClient, queryKeys.notifications(), notification)
    // Keeps the "Follow Requests (N)" count on the owner's profile live instead of stale until reload.
    if (notification.type === 'FOLLOW_REQUEST') {
      queryClient.invalidateQueries({ queryKey: queryKeys.followRequests() })
    }
  })

  stomp.subscribe('/user/queue/messages', (frame: IMessage) => {
    const message = JSON.parse(frame.body) as Message
    prependToFirstPage(queryClient, queryKeys.messages(message.conversationId), message)
    queryClient.invalidateQueries({ queryKey: queryKeys.conversations() })
  })

  stomp.subscribe('/user/queue/errors', (frame: IMessage) => {
    console.error('STOMP error', frame.body)
  })
}
