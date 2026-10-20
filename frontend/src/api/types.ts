export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface CurrentWeek {
  season: number
  week: number
}

/** The RFC 7807 body the backend returns for every error; only `detail` is shown to people. */
export interface ApiProblem {
  status?: number
  detail?: string
}
