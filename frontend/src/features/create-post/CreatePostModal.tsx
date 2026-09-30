import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from '@/components/Avatar'
import { Icon } from '@/components/Icon'
import { Modal } from '@/components/Modal'
import * as postsApi from '@/lib/api/endpoints/posts'
import * as reelsApi from '@/lib/api/endpoints/reels'
import { ApiError } from '@/lib/api/client'
import { useAuth } from '@/contexts/useAuth'
import { queryKeys } from '@/lib/queryKeys'
import styles from './CreatePostModal.module.css'

const ALLOWED_TYPE_PATTERN = /^image\/(jpeg|png|webp)$|^video\/(mp4|quicktime|webm)$/
const CAPTION_MAX = 2200
const LOCATION_MAX = 255

interface CreatePostInput {
  file: File
  caption: string
  location: string
}

export function CreatePostModal() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [caption, setCaption] = useState('')
  const [location, setLocation] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: async ({ file, caption, location }: CreatePostInput) => {
      const isVideo = file.type.startsWith('video/')
      const upload = await postsApi.createUploadUrl({ contentType: file.type })
      await postsApi.uploadToPresignedUrl(upload.uploadUrl, file)
      if (isVideo) {
        return reelsApi.createReel({
          caption: caption || undefined,
          location: location || undefined,
          media: { url: upload.publicUrl },
        })
      }
      return postsApi.createPost({
        caption: caption || undefined,
        location: location || undefined,
        media: { url: upload.publicUrl },
      })
    },
    onSuccess: (post) => {
      if (user) {
        queryClient.invalidateQueries({ queryKey: queryKeys.userPosts(user.username) })
        queryClient.invalidateQueries({ queryKey: queryKeys.userProfile(user.username) })
      }
      navigate(post.type === 'REEL' ? '/reels' : `/${user?.username}`)
    },
  })

  function handleClose() {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
    navigate(-1)
  }

  function selectFile(selected: File | null | undefined) {
    if (!selected) return
    if (!ALLOWED_TYPE_PATTERN.test(selected.type)) {
      setValidationError('Please select a JPEG, PNG, WebP image or MP4, MOV, WebM video.')
      return
    }
    setValidationError(null)
    setFile(selected)
    setPreviewUrl(URL.createObjectURL(selected))
  }

  function handleShare() {
    if (!file) return
    createMutation.mutate({ file, caption, location })
  }

  const isVideo = file?.type.startsWith('video/')

  return (
    <Modal onClose={handleClose} contentClassName={styles.content} labelledBy="create-post-title">
      <div className={styles.header}>
        <span id="create-post-title">Create new post</span>
        {file ? (
          <button type="button" className={styles.shareButton} onClick={handleShare} disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Sharing…' : 'Share'}
          </button>
        ) : null}
      </div>

      {!file ? (
        <div
          className={styles.selectStep}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault()
            selectFile(e.dataTransfer.files[0])
          }}
        >
          <Icon name="video" size={64} className={styles.dropzoneIcon} />
          <p className={styles.dropzoneText}>Drag photos and videos here</p>
          {validationError ? <p className={styles.errorText}>{validationError}</p> : null}
          <button type="button" onClick={() => fileInputRef.current?.click()}>
            <span style={{ color: 'var(--color-accent)', fontWeight: 'var(--font-weight-semibold)' }}>
              Select from computer
            </span>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm"
            hidden
            onChange={(e) => selectFile(e.target.files?.[0])}
          />
        </div>
      ) : (
        <div className={styles.detailsStep}>
          <div className={styles.previewColumn}>
            {isVideo ? <video src={previewUrl ?? undefined} controls /> : <img src={previewUrl ?? undefined} alt="Selected upload preview" />}
          </div>
          <div className={styles.detailsColumn}>
            <div className={styles.authorRow}>
              <Avatar src={user?.profilePictureUrl} alt={user?.username ?? ''} size={32} />
              <span className={styles.username}>{user?.username}</span>
            </div>
            <textarea
              className={styles.captionInput}
              placeholder="Write a caption…"
              value={caption}
              maxLength={CAPTION_MAX}
              onChange={(e) => setCaption(e.target.value)}
            />
            <span className={styles.charCount}>
              {caption.length}/{CAPTION_MAX}
            </span>
            <input
              className={styles.locationInput}
              placeholder="Add location"
              value={location}
              maxLength={LOCATION_MAX}
              onChange={(e) => setLocation(e.target.value)}
            />
            {createMutation.isPending ? <p className={styles.progressText}>Uploading…</p> : null}
            {createMutation.isError ? (
              <p className={styles.errorText}>
                {createMutation.error instanceof ApiError ? createMutation.error.detail : 'Something went wrong.'}
              </p>
            ) : null}
          </div>
        </div>
      )}
    </Modal>
  )
}
