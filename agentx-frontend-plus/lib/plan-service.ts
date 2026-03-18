import { API_ENDPOINTS } from "./api-config"
import { httpClient } from "@/lib/http-client"
import type { ApiResponse } from "@/types/agent"
import type { PlanView } from "@/types/plan"

export async function getSessionPlan(sessionId: string): Promise<ApiResponse<PlanView | null>> {
  try {
    const data = await httpClient.get<ApiResponse<PlanView | null>>(
      API_ENDPOINTS.SESSION_PLAN(sessionId)
    )
    return data
  } catch (error) {
    return {
      code: 500,
      message: error instanceof Error ? error.message : "Failed to load plan",
      data: null,
      timestamp: Date.now(),
    }
  }
}
