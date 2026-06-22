import { request } from './http'
import type { LoginRequest, LoginResponse, RegisterRequest } from './types'

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>({ url: '/auth/login', method: 'POST', data: payload })
}

export async function register(payload: RegisterRequest): Promise<void> {
  return request<void>({ url: '/auth/register', method: 'POST', data: payload })
}
