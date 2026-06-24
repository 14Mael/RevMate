import { request } from './http'
import type {
  CourseCard,
  CourseKeywordRequest,
  CourseRecommendRequest,
  SavedCourse,
  SavedCourseRequest
} from './types'

export function extractCourseKeywords(materialId: number): Promise<string[]> {
  const data: CourseKeywordRequest = { materialId }
  // 关键词提炼是一次大模型调用，远超默认 30s，参考出题接口放宽超时
  return request<string[]>({
    url: '/courses/keywords',
    method: 'POST',
    data,
    timeout: 120000
  })
}

export function recommendCourses(keywords: string[]): Promise<CourseCard[]> {
  const data: CourseRecommendRequest = { keywords }
  // 搜索 + 大模型整理两段串行慢调用，放宽超时避免后端成功前端却报超时
  return request<CourseCard[]>({
    url: '/courses/recommend',
    method: 'POST',
    data,
    timeout: 120000
  })
}

export function saveCourse(course: SavedCourseRequest): Promise<SavedCourse> {
  return request<SavedCourse>({
    url: '/courses/saved',
    method: 'POST',
    data: course
  })
}

export function listSavedCourses(): Promise<SavedCourse[]> {
  return request<SavedCourse[]>({
    url: '/courses/saved',
    method: 'GET'
  })
}

export function deleteSavedCourse(id: number): Promise<void> {
  return request<void>({
    url: `/courses/saved/${id}`,
    method: 'DELETE'
  })
}
