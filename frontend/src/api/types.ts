/**
 * RevMate API 类型定义
 * 与后端 DTO 保持 1:1（RevMate/backend/src/main/java/.../dto/）
 * 修改时必须同步后端 DTO 与 Plan 0 接口契约。
 */

/** 统一后端返回包装 */
export interface Result<T> {
  code: number
  message: string
  data: T
}

/* ========== 鉴权 ========== */

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
}

/* ========== 学科 ========== */

export interface Subject {
  id: number
  name: string
  createdAt: string
}

export interface CreateSubjectRequest {
  name: string
}

/* ========== 资料 ========== */

export type MaterialType = 'txt' | 'pdf' | 'word' | 'ppt' | 'excel' | 'image' | 'audio' | 'unknown'

export type MaterialStatus = 'PROCESSING' | 'READY' | 'FAILED'

export type PreviewStatus = 'NONE' | 'PROCESSING' | 'READY' | 'FAILED'

export interface Material {
  id: number
  subjectId: number
  filename: string
  type: MaterialType
  status: MaterialStatus
  createdAt: string
  previewable: boolean
  previewStatus?: PreviewStatus
  previewMessage?: string
  course?: string
}

/* ========== 问答 ========== */

export type SourceType = 'material' | 'web'
export type AnswerMode = 'material' | 'general' | 'web'

export interface ChatHistoryMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp?: string
}

export interface ChatHistoryItem {
  id: string
  title: string
  messages: ChatHistoryMessage[]
  createdAt: string
  subjectId: number
  course?: string
}

export interface Source {
  type: SourceType
  title: string
  snippet: string
  url?: string
  materialId?: number
  page?: string
}

export interface ChatRequest {
  subjectId: number
  materialId?: number
  question: string
  history?: ChatHistoryMessage[]
}

export interface ChatResponse {
  answer: string
  sources: Source[]
  answerMode: AnswerMode
}

export interface ChatStreamEvent {
  type: 'delta' | 'done'
  text?: string | null
  sources?: Source[]
  answerMode?: AnswerMode
}

/* ========== 出题 ========== */

export type QuizType = 'single' | 'fill' | 'qa'

export interface QuizRequest {
  subjectId: number
  materialId?: number
  type: QuizType
  count: number
}

export interface Question {
  stem: string
  options?: string[]
  answer: string
  analysis: string
}

export interface QuizResponse {
  questions: Question[]
}
