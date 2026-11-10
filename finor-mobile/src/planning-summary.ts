export type IncomeSchedule = {
  monthly_amount: number | string;
  next_income: string;
  remaining_essentials: number | string;
  safety_margin: number | string;
};

export type Available = {
  available: number;
  balance: number;
  pending: number;
  reserves: number;
  essentials: number;
  margin: number;
  suggestedDaily: number;
};

export type PlanningSummary = {
  schedule: IncomeSchedule | null;
  available: Available | null;
  needsUpdate: boolean;
};

export function planningToday() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Sao_Paulo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

export function incomeDateFromInput(value: string) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value.trim());
  if (!match) return null;
  const iso = `${match[3]}-${match[2]}-${match[1]}`;
  const parsed = new Date(`${iso}T12:00:00Z`);
  return !Number.isNaN(parsed.getTime()) &&
    parsed.toISOString().slice(0, 10) === iso
    ? iso
    : null;
}

export async function loadPlanningSummary(
  fetchApi: <T>(path: string) => Promise<T>,
  today = planningToday(),
): Promise<PlanningSummary> {
  const schedules = await fetchApi<IncomeSchedule[]>("/planning/schedule");
  const schedule = schedules[0] ?? null;
  if (!schedule) return { schedule: null, available: null, needsUpdate: false };
  if (schedule.next_income.slice(0, 10) <= today)
    return { schedule, available: null, needsUpdate: true };
  return {
    schedule,
    available: await fetchApi<Available>("/planning/available"),
    needsUpdate: false,
  };
}
