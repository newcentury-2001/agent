export interface PlanStep {
  stepNo: number
  title: string
  detail?: string
  status: string
  result?: string
}

export interface PlanView {
  id: string
  sessionId: string
  title: string
  goal?: string
  status: string
  currentStep?: number
  steps: PlanStep[]
}
