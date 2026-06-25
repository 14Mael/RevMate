import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { Material, Subject } from '../types'

// Mock http and the cross-module subject call.
const requestMock = vi.fn()
const listSubjectsMock = vi.fn()
vi.mock('../http', () => ({
  request: (cfg: unknown) => requestMock(cfg),
  default: {
    get: vi.fn(),
    create: vi.fn()
  }
}))
vi.mock('../subject', () => ({
  listSubjects: () => listSubjectsMock()
}))

// Import lazily so the mocks above are registered first.
import { uploadMaterial, listMaterials, deleteMaterial, canPreview, listCourses } from '../material'

const subjects: Subject[] = [
  { id: 1, name: 'Java', createdAt: '2026-06-01T00:00:00Z' },
  { id: 2, name: 'OS', createdAt: '2026-06-01T00:00:00Z' }
]

const baseMaterial: Material = {
  id: 100,
  subjectId: 1,
  filename: 'note.txt',
  type: 'txt',
  status: 'READY',
  createdAt: '2026-06-22T10:00:00Z',
  previewable: true,
  previewStatus: 'READY'
}

beforeEach(() => {
  requestMock.mockReset()
  listSubjectsMock.mockReset()
})

describe('listMaterials', () => {
  it('returns materials for all subjects sorted by createdAt desc', async () => {
    listSubjectsMock.mockResolvedValue(subjects)
    requestMock.mockImplementation((cfg: any) => {
      const subjectId = cfg.params.subjectId
      return Promise.resolve(
        subjectId === 1
          ? [{ ...baseMaterial, createdAt: '2026-06-22T10:00:00Z' }]
          : [{ ...baseMaterial, id: 101, subjectId: 2, createdAt: '2026-06-21T10:00:00Z' }]
      )
    })

    const result = await listMaterials()
    expect(result).toHaveLength(2)
    expect(result[0].id).toBe(100) // newer first
    expect(result[0].course).toBe('Java')
    expect(result[1].course).toBe('OS')
  })

  it('filters by subjectId when provided', async () => {
    listSubjectsMock.mockResolvedValue(subjects)
    requestMock.mockResolvedValueOnce([baseMaterial])

    const result = await listMaterials(1)
    expect(requestMock).toHaveBeenCalledTimes(1)
    expect(requestMock.mock.calls[0][0].params).toEqual({ subjectId: 1 })
    expect(result).toHaveLength(1)
  })
})

describe('uploadMaterial', () => {
  it('appends file and subjectId to FormData and decorates with course', async () => {
    listSubjectsMock.mockResolvedValue(subjects)
    requestMock.mockResolvedValueOnce({ ...baseMaterial, subjectId: 1 })

    const file = new File(['content'], 'note.txt', { type: 'text/plain' })
    const result = await uploadMaterial(file, 1)

    expect(result.course).toBe('Java')
    const cfg = requestMock.mock.calls[0][0]
    expect(cfg.url).toBe('/materials')
    expect(cfg.method).toBe('POST')
    expect(cfg.data).toBeInstanceOf(FormData)
    const form = cfg.data as FormData
    expect(form.get('file')).toBe(file)
    expect(form.get('subjectId')).toBe('1')
  })
})

describe('deleteMaterial', () => {
  it('DELETEs /materials/{id}', async () => {
    requestMock.mockResolvedValue(undefined)
    await deleteMaterial(42)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/materials/42',
      method: 'DELETE'
    })
  })
})

describe('canPreview', () => {
  it('returns true only when previewable and READY', () => {
    expect(canPreview({ ...baseMaterial, previewable: true, previewStatus: 'READY' })).toBe(true)
    expect(canPreview({ ...baseMaterial, previewable: true, previewStatus: 'FAILED' })).toBe(false)
    expect(canPreview({ ...baseMaterial, previewable: false, previewStatus: 'READY' })).toBe(false)
  })
})

describe('listCourses', () => {
  it('returns unique courses sorted by frequency desc', () => {
    const materials: Material[] = [
      { ...baseMaterial, course: 'Java' },
      { ...baseMaterial, id: 2, course: 'Java' },
      { ...baseMaterial, id: 3, course: 'OS' }
    ]
    const result = listCourses(materials)
    expect(result).toEqual(['Java', 'OS'])
  })

  it('ignores materials without a course', () => {
    const materials: Material[] = [
      { ...baseMaterial, course: undefined },
      { ...baseMaterial, id: 2, course: 'Java' }
    ]
    expect(listCourses(materials)).toEqual(['Java'])
  })
})
