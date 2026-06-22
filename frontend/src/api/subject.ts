import { request } from './http'
import type { CreateSubjectRequest, Subject } from './types'

export function listSubjects(): Promise<Subject[]> {
  return request<Subject[]>({ url: '/subjects', method: 'GET' })
}

export function createSubject(payload: CreateSubjectRequest): Promise<Subject> {
  return request<Subject>({ url: '/subjects', method: 'POST', data: payload })
}

export async function findOrCreateSubject(name: string): Promise<Subject> {
  const normalized = name.trim()
  if (!normalized) {
    throw new Error('课程名称不能为空')
  }

  const subjects = await listSubjects()
  const existing = subjects.find((subject) => subject.name === normalized)
  if (existing) return existing

  return createSubject({ name: normalized })
}
