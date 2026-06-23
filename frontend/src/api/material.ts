import http, { request } from './http'
import { listSubjects } from './subject'
import type { Material, Subject } from './types'

function attachCourse(material: Material, subjects: Subject[]): Material {
  return {
    ...material,
    course: subjects.find((subject) => subject.id === material.subjectId)?.name
  }
}

export async function listMaterials(subjectId?: number): Promise<Material[]> {
  const subjects = await listSubjects()
  const targetSubjects = subjectId ? subjects.filter((subject) => subject.id === subjectId) : subjects
  const groups = await Promise.all(
    targetSubjects.map(async (subject) => {
      const list = await request<Material[]>({
        url: '/materials',
        method: 'GET',
        params: { subjectId: subject.id }
      })
      return list.map((material) => attachCourse(material, subjects))
    })
  )
  return groups.flat().sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
}

export async function uploadMaterial(file: File, subjectId: number): Promise<Material> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('subjectId', String(subjectId))

  const material = await request<Material>({
    url: '/materials',
    method: 'POST',
    data: formData
  })
  const subjects = await listSubjects()
  return attachCourse(material, subjects)
}

export function deleteMaterial(id: number): Promise<void> {
  return request<void>({ url: `/materials/${id}`, method: 'DELETE' })
}

export async function getMaterial(id: number): Promise<Material | undefined> {
  const materials = await listMaterials()
  return materials.find((material) => material.id === id)
}

export async function getPreviewUrl(id: number): Promise<string> {
  const response = await http.get<Blob>(`/materials/${id}/preview`, { responseType: 'blob' })
  return URL.createObjectURL(response.data)
}

export function canPreview(material: Material): boolean {
  return material.previewable && material.previewStatus === 'READY'
}

export function listCourses(materials: Material[]): string[] {
  const counts = new Map<string, number>()
  materials.forEach((material) => {
    if (material.course) counts.set(material.course, (counts.get(material.course) || 0) + 1)
  })
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([name]) => name)
}
