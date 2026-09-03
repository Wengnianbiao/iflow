import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent,
  type PointerEvent as ReactPointerEvent,
} from 'react'
import { ArrowsInIcon, ArrowsOutIcon, PaletteIcon, PaperPlaneRightIcon, XIcon } from '@phosphor-icons/react'
import styles from './RobotFloat.module.css'

type ChatRole = 'bot' | 'user'
type SkinId = 'doge' | 'robot' | 'fox'
type MascotMood = 'idle' | 'happy' | 'drag' | 'open' | 'hover'

type ChatMessage = {
  id: string
  role: ChatRole
  text: string
}

type FabPosition = {
  x: number
  y: number
}

type SkinConfig = {
  id: SkinId
  label: string
  title: string
  subtitle: string
  hello: string
  welcome: string
  tip: string
  reply: string
}

const FAB_SIZE = 64
const PANEL_GAP = 14
const EDGE_PAD = 16
const DRAG_THRESHOLD = 6
const SKIN_STORAGE_KEY = 'iflow-robot-skin'

const SKINS: SkinConfig[] = [
  {
    id: 'doge',
    label: '狗狗币',
    title: 'iFlow Doge',
    subtitle: '可拖拽 · 可全屏 · Much wow · 接口待接入',
    hello: 'wow',
    welcome: '汪！我是 iFlow 狗狗助手～可以拖我到处逛，点我开聊；会话还能全屏。Such helpful. Much wow.',
    tip: '目前只是样式预览，接口地址稍后接入。先随便发条消息玩玩～ Very chat. Wow.',
    reply: '汪汪～收到！接口还没接上，这是本地样式预览回复。Much reply. So style. Wow!',
  },
  {
    id: 'robot',
    label: '机器人',
    title: 'iFlow 助手',
    subtitle: '可拖拽 · 可全屏 · 接口待接入',
    hello: 'hi~',
    welcome: '你好呀～我是 iFlow 助手！可以拖我到处逛，点我开聊；会话还能全屏哦。',
    tip: '目前只是样式预览，接口地址稍后接入。先随便发条消息玩玩～',
    reply: '收到啦～接口还没接上，这是本地样式预览回复。继续戳我玩动画也可以！',
  },
  {
    id: 'fox',
    label: '小狐狸',
    title: 'iFlow 小狐狸',
    subtitle: '可拖拽 · 可全屏 · 灵巧机敏 · 接口待接入',
    hello: '嘿!',
    welcome: '嗨～我是小狐狸助手。拖我移动，点我聊天，会话还能全屏。',
    tip: '目前只是样式预览，接口地址稍后接入。换个皮肤也行哦～',
    reply: '收到！接口还没接上，先看样式。换皮肤点右上角调色板就好～',
  },
]

const SKIN_CLASS: Record<SkinId, string> = {
  doge: styles.skinDoge,
  robot: styles.skinRobot,
  fox: styles.skinFox,
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function defaultPosition(): FabPosition {
  if (typeof window === 'undefined') {
    return { x: 28, y: 28 }
  }
  return {
    x: window.innerWidth - FAB_SIZE - 28,
    y: window.innerHeight - FAB_SIZE - 28,
  }
}

function readStoredSkin(): SkinId {
  try {
    const value = window.localStorage.getItem(SKIN_STORAGE_KEY)
    if (value === 'doge' || value === 'robot' || value === 'fox') return value
  } catch {
    // ignore
  }
  return 'doge'
}

function getSkin(id: SkinId) {
  return SKINS.find((skin) => skin.id === id) ?? SKINS[0]
}

function moodClassName(mood: MascotMood) {
  if (mood === 'happy') return styles.mood_happy
  if (mood === 'drag') return styles.mood_drag
  if (mood === 'open') return styles.mood_open
  if (mood === 'hover') return styles.mood_hover
  return styles.mood_idle
}

function RobotMascot({
  skin,
  mood,
  lookX = 0,
  lookY = 0,
}: {
  skin: SkinId
  mood: MascotMood
  lookX?: number
  lookY?: number
}) {
  const eyeOffset = {
    leftEye: {
      left: `${8 + lookX * 2}px`,
      top: `${11 + lookY * 1.5}px`,
    },
    rightEye: {
      right: `${8 - lookX * 2}px`,
      left: 'auto',
      top: `${11 + lookY * 1.5}px`,
    },
  }

  if (skin === 'robot') {
    return (
      <span className={`${styles.mascot} ${styles.mascotRobot} ${moodClassName(mood)}`} aria-hidden="true">
        <span className={styles.antenna}>
          <span className={styles.antennaBall} />
        </span>
        <span className={styles.face}>
          <span className={styles.eye} style={eyeOffset.leftEye} />
          <span className={`${styles.eye} ${styles.eyeRight}`} style={eyeOffset.rightEye} />
          <span className={styles.mouth} />
          <span className={styles.cheek} />
          <span className={`${styles.cheek} ${styles.cheekRight}`} />
        </span>
        <span className={styles.ear} />
        <span className={`${styles.ear} ${styles.earRight}`} />
      </span>
    )
  }

  if (skin === 'fox') {
    return (
      <span className={`${styles.mascot} ${styles.mascotFox} ${moodClassName(mood)}`} aria-hidden="true">
        <span className={styles.ear} />
        <span className={`${styles.ear} ${styles.earRight}`} />
        <span className={styles.face}>
          <span className={styles.snout} />
          <span className={styles.eye} style={eyeOffset.leftEye} />
          <span className={`${styles.eye} ${styles.eyeRight}`} style={eyeOffset.rightEye} />
          <span className={styles.nose} />
          <span className={styles.mouth} />
          <span className={styles.cheek} />
          <span className={`${styles.cheek} ${styles.cheekRight}`} />
        </span>
      </span>
    )
  }

  return (
    <span className={`${styles.mascot} ${styles.mascotDoge} ${moodClassName(mood)}`} aria-hidden="true">
      <span className={styles.ear} />
      <span className={`${styles.ear} ${styles.earRight}`} />
      <span className={styles.face}>
        <span className={styles.facePatch} />
        <span className={`${styles.facePatch} ${styles.facePatchRight}`} />
        <span className={styles.snout} />
        <span className={styles.eye} style={eyeOffset.leftEye} />
        <span className={`${styles.eye} ${styles.eyeRight}`} style={eyeOffset.rightEye} />
        <span className={styles.nose} />
        <span className={styles.mouth} />
        <span className={styles.cheek} />
        <span className={`${styles.cheek} ${styles.cheekRight}`} />
      </span>
      <span className={styles.coin}>Ð</span>
    </span>
  )
}

export function RobotFloat() {
  const [open, setOpen] = useState(false)
  const [fullscreen, setFullscreen] = useState(false)
  const [skinPickerOpen, setSkinPickerOpen] = useState(false)
  const [skinId, setSkinId] = useState<SkinId>(() =>
    typeof window === 'undefined' ? 'doge' : readStoredSkin(),
  )
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>(() => {
    const skin = getSkin(typeof window === 'undefined' ? 'doge' : readStoredSkin())
    return [
      { id: 'welcome', role: 'bot', text: skin.welcome },
      { id: 'tip', role: 'bot', text: skin.tip },
    ]
  })
  const [position, setPosition] = useState<FabPosition>(defaultPosition)
  const [dragging, setDragging] = useState(false)
  const [happy, setHappy] = useState(false)
  const [hovered, setHovered] = useState(false)
  const [look, setLook] = useState({ x: 0, y: 0 })
  const [viewport, setViewport] = useState(() => ({
    width: typeof window === 'undefined' ? 1280 : window.innerWidth,
    height: typeof window === 'undefined' ? 800 : window.innerHeight,
  }))
  const listRef = useRef<HTMLDivElement>(null)
  const dragRef = useRef<{
    pointerId: number
    startX: number
    startY: number
    originX: number
    originY: number
    moved: boolean
  } | null>(null)
  const titleId = useId()
  const skin = getSkin(skinId)
  const themeClass = SKIN_CLASS[skinId]

  useEffect(() => {
    const onResize = () => {
      setViewport({ width: window.innerWidth, height: window.innerHeight })
      setPosition((current) => ({
        x: clamp(current.x, EDGE_PAD, window.innerWidth - FAB_SIZE - EDGE_PAD),
        y: clamp(current.y, EDGE_PAD, window.innerHeight - FAB_SIZE - EDGE_PAD),
      }))
    }
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  useEffect(() => {
    if (!open) return
    const node = listRef.current
    if (!node) return
    node.scrollTop = node.scrollHeight
  }, [messages, open, fullscreen])

  useEffect(() => {
    if (!happy) return
    const timer = window.setTimeout(() => setHappy(false), 900)
    return () => window.clearTimeout(timer)
  }, [happy])

  useEffect(() => {
    if (!open) setSkinPickerOpen(false)
  }, [open])

  const selectSkin = (nextId: SkinId) => {
    if (nextId === skinId) {
      setSkinPickerOpen(false)
      return
    }
    const next = getSkin(nextId)
    setSkinId(nextId)
    setSkinPickerOpen(false)
    setHappy(true)
    try {
      window.localStorage.setItem(SKIN_STORAGE_KEY, nextId)
    } catch {
      // ignore
    }
    setMessages((current) => [
      ...current,
      {
        id: `skin-${Date.now()}`,
        role: 'bot',
        text: `已切换到「${next.label}」皮肤～${next.hello}`,
      },
    ])
  }

  const panelStyle = (() => {
    if (fullscreen) return undefined
    const panelWidth = Math.min(380, viewport.width - 40)
    const panelHeight = Math.min(560, viewport.height - 140)
    const preferAbove = position.y > panelHeight + PANEL_GAP + EDGE_PAD
    const top = preferAbove
      ? position.y - panelHeight - PANEL_GAP
      : position.y + FAB_SIZE + PANEL_GAP
    const left = clamp(
      position.x + FAB_SIZE - panelWidth,
      EDGE_PAD,
      viewport.width - panelWidth - EDGE_PAD,
    )
    return {
      top,
      left,
      width: panelWidth,
      height: panelHeight,
      right: 'auto' as const,
      bottom: 'auto' as const,
    }
  })()

  const updateLook = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const rect = event.currentTarget.getBoundingClientRect()
    const x = ((event.clientX - rect.left) / rect.width) * 2 - 1
    const y = ((event.clientY - rect.top) / rect.height) * 2 - 1
    setLook({
      x: clamp(x, -1, 1),
      y: clamp(y, -1, 1),
    })
  }

  const onPointerDown = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (event.button !== 0) return
    event.currentTarget.setPointerCapture(event.pointerId)
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: position.x,
      originY: position.y,
      moved: false,
    }
  }

  const onPointerMove = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (!dragging) updateLook(event)

    const drag = dragRef.current
    if (!drag || drag.pointerId !== event.pointerId) return

    const dx = event.clientX - drag.startX
    const dy = event.clientY - drag.startY
    if (!drag.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return

    drag.moved = true
    setDragging(true)
    setPosition({
      x: clamp(drag.originX + dx, EDGE_PAD, window.innerWidth - FAB_SIZE - EDGE_PAD),
      y: clamp(drag.originY + dy, EDGE_PAD, window.innerHeight - FAB_SIZE - EDGE_PAD),
    })
  }

  const finishPointer = useCallback((event: ReactPointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current
    if (!drag || drag.pointerId !== event.pointerId) return

    const wasDrag = drag.moved
    dragRef.current = null
    setDragging(false)

    if (wasDrag) return

    setOpen((current) => {
      const next = !current
      if (next) setHappy(true)
      if (!next) {
        setFullscreen(false)
        setSkinPickerOpen(false)
      }
      return next
    })
  }, [])

  const sendMessage = () => {
    const text = draft.trim()
    if (!text) return

    setMessages((current) => [
      ...current,
      { id: `user-${Date.now()}`, role: 'user', text },
      { id: `bot-${Date.now() + 1}`, role: 'bot', text: skin.reply },
    ])
    setDraft('')
    setHappy(true)
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    sendMessage()
  }

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      sendMessage()
    }
  }

  const mood: MascotMood = dragging
    ? 'drag'
    : happy
      ? 'happy'
      : open
        ? 'open'
        : hovered
          ? 'hover'
          : 'idle'

  return (
    <>
      {open ? (
        <section
          className={`${styles.panel} ${themeClass} ${fullscreen ? styles.panelFullscreen : ''}`}
          style={panelStyle}
          aria-labelledby={titleId}
          role="dialog"
          aria-modal={fullscreen}
        >
          <header className={styles.header}>
            <div className={styles.avatar} aria-hidden="true">
              <RobotMascot skin={skinId} mood={happy ? 'happy' : 'open'} />
            </div>
            <div className={styles.headerText}>
              <h2 className={styles.title} id={titleId}>
                {skin.title}
              </h2>
              <p className={styles.subtitle}>{skin.subtitle}</p>
            </div>
            <div className={styles.headerActions}>
              <button
                type="button"
                className={`${styles.closeButton} ${skinPickerOpen ? styles.closeButtonActive : ''}`}
                aria-label="切换皮肤"
                title="切换皮肤"
                aria-expanded={skinPickerOpen}
                onClick={() => setSkinPickerOpen((current) => !current)}
              >
                <PaletteIcon size={16} weight="bold" />
              </button>
              <button
                type="button"
                className={styles.closeButton}
                aria-label={fullscreen ? '退出全屏' : '全屏会话'}
                title={fullscreen ? '退出全屏' : '全屏会话'}
                onClick={() => setFullscreen((current) => !current)}
              >
                {fullscreen ? <ArrowsInIcon size={16} weight="bold" /> : <ArrowsOutIcon size={16} weight="bold" />}
              </button>
              <button
                type="button"
                className={styles.closeButton}
                aria-label="关闭助手"
                onClick={() => {
                  setOpen(false)
                  setFullscreen(false)
                  setSkinPickerOpen(false)
                }}
              >
                <XIcon size={16} weight="bold" />
              </button>
              {skinPickerOpen ? (
                <div className={styles.skinPicker} role="listbox" aria-label="皮肤列表">
                  {SKINS.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      role="option"
                      aria-selected={item.id === skinId}
                      className={`${styles.skinOption} ${item.id === skinId ? styles.skinOptionActive : ''}`}
                      onClick={() => selectSkin(item.id)}
                    >
                      <span className={`${styles.skinPreview} ${SKIN_CLASS[item.id]}`}>
                        <RobotMascot skin={item.id} mood="open" />
                      </span>
                      <span className={styles.skinLabel}>{item.label}</span>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
          </header>

          <div className={styles.messages} ref={listRef}>
            {messages.map((message) => (
              <div
                key={message.id}
                className={`${styles.bubbleRow} ${message.role === 'user' ? styles.bubbleRowUser : ''}`}
              >
                {message.role === 'bot' ? (
                  <div className={styles.bubbleAvatar} aria-hidden="true">
                    <RobotMascot skin={skinId} mood="open" />
                  </div>
                ) : null}
                <div
                  className={`${styles.bubble} ${
                    message.role === 'bot' ? styles.bubbleBot : styles.bubbleUser
                  } ${fullscreen ? styles.bubbleWide : ''}`}
                >
                  {message.text}
                </div>
              </div>
            ))}
          </div>

          <form className={styles.composer} onSubmit={onSubmit}>
            <textarea
              className={styles.input}
              value={draft}
              rows={fullscreen ? 2 : 1}
              placeholder="输入消息预览样式…"
              aria-label="消息输入"
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={onKeyDown}
            />
            <button
              type="submit"
              className={styles.sendButton}
              aria-label="发送"
              disabled={!draft.trim()}
            >
              <PaperPlaneRightIcon size={18} weight="fill" />
            </button>
          </form>
          <p className={styles.hint}>
            Enter 发送 · 调色板切换皮肤 · 拖拽悬浮球移动 · 暂不调用后端
          </p>
        </section>
      ) : null}

      <button
        type="button"
        className={`${styles.fab} ${themeClass} ${open ? styles.fabOpen : ''} ${
          dragging ? styles.fabDragging : ''
        } ${happy ? styles.fabHappy : ''} ${hovered && !open && !dragging ? styles.fabHover : ''}`}
        style={{ left: position.x, top: position.y, right: 'auto', bottom: 'auto' }}
        aria-label={open ? '收起助手' : '打开助手'}
        aria-expanded={open}
        title="拖我移动，点我聊天"
        onPointerEnter={(event) => {
          setHovered(true)
          updateLook(event)
        }}
        onPointerLeave={() => {
          setHovered(false)
          setLook({ x: 0, y: 0 })
        }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={finishPointer}
        onPointerCancel={finishPointer}
      >
        {!open && hovered && !dragging ? (
          <span className={styles.helloBubble} aria-hidden="true">
            {skin.hello}
          </span>
        ) : null}
        <span className={styles.fabIcon}>
          {open ? (
            <XIcon size={22} weight="bold" />
          ) : (
            <RobotMascot skin={skinId} mood={mood} lookX={look.x} lookY={look.y} />
          )}
        </span>
      </button>
    </>
  )
}
