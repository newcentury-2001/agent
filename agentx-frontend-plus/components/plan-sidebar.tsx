"use client"

import { useEffect, useMemo, useState } from "react"
import { CheckCircle2, Circle, Loader2, XCircle } from "lucide-react"
import { getSessionPlan } from "@/lib/plan-service"
import type { PlanView, PlanStep } from "@/types/plan"

interface PlanSidebarProps {
  conversationId?: string
  placeholderSteps?: PlanStep[]
  position?: "fixed" | "absolute"
}

function statusBadge(status?: string) {
  switch (status) {
    case "DONE":
      return "bg-green-100 text-green-700"
    case "DOING":
      return "bg-blue-100 text-blue-700"
    case "FAILED":
      return "bg-red-100 text-red-700"
    case "SKIPPED":
      return "bg-gray-100 text-gray-600"
    default:
      return "bg-yellow-50 text-yellow-700"
  }
}

function statusIcon(status?: string) {
  switch (status) {
    case "DONE":
      return <CheckCircle2 className="h-4 w-4 text-green-600" />
    case "DOING":
      return <Loader2 className="h-4 w-4 text-blue-600 animate-spin" />
    case "FAILED":
      return <XCircle className="h-4 w-4 text-red-600" />
    default:
      return <Circle className="h-4 w-4 text-gray-400" />
  }
}

export function PlanSidebar({ conversationId, placeholderSteps, position = "fixed" }: PlanSidebarProps) {
  const [plan, setPlan] = useState<PlanView | null>(null)

  useEffect(() => {
    if (!conversationId) return
    let timer: ReturnType<typeof setInterval> | null = null
    const fetchPlan = async () => {
      const res = await getSessionPlan(conversationId)
      if (res.code === 200) {
        setPlan(res.data || null)
      }
    }

    fetchPlan()
    timer = setInterval(fetchPlan, 5000)
    return () => {
      if (timer) clearInterval(timer)
    }
  }, [conversationId])

  const steps = useMemo(() => plan?.steps || placeholderSteps || [], [plan, placeholderSteps])

  if (steps.length === 0) {
    return null
  }

  const positionClass = position === "absolute" ? "absolute" : "fixed"

  return (
    <div className={`${positionClass} right-4 top-20 z-40 w-[320px] max-h-[calc(100vh-6rem)] overflow-hidden rounded-xl bg-white/95 shadow-lg ring-1 ring-black/5 flex flex-col`}>
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {steps.map((step: PlanStep) => (
          <div key={step.stepNo} className="border rounded-lg p-3">
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-start gap-2">
                {statusIcon(step.status)}
                <div className="text-sm font-medium text-gray-900">
                  {step.stepNo}. {step.title}
                </div>
              </div>
              <span className={`text-xs px-2 py-0.5 rounded ${statusBadge(step.status)}`}>
                {step.status || "TODO"}
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
