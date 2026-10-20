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

export type Position = 'QB' | 'RB' | 'WR' | 'TE'
export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW'

export interface Factor {
  factorType: string
  contribution: number
  narrative: string
}

/** One player's score for one week; the factors' contributions add up to `score`. */
export interface StartSitRecommendation {
  playerId: string
  playerName: string
  position: Position
  team: string | null
  score: number
  confidence: Confidence
  factors: Factor[]
}
