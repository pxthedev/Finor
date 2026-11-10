export type Installment = {
  id: string;
  name: string;
  installment_amount: number | string;
  total_installments: number;
  paid_installments: number;
  first_due_date: string;
};

export function installmentDueDate(first: string, index: number) {
  const [year, month, day] = first.slice(0, 10).split("-").map(Number);
  const target = new Date(Date.UTC(year, month - 1 + index, 1));
  const lastDay = new Date(
    Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0),
  ).getUTCDate();
  target.setUTCDate(Math.min(day, lastDay));
  return target.toISOString().slice(0, 10);
}

export function installmentProgress(plan: Installment) {
  const paid = Number(plan.paid_installments),
    total = Number(plan.total_installments);
  const remaining = Math.max(0, total - paid);
  return {
    remaining,
    remainingAmount:
      (Math.round(Number(plan.installment_amount) * 100) * remaining) / 100,
    progress: total > 0 ? Math.min(100, (paid / total) * 100) : 0,
    nextDueDate: remaining
      ? installmentDueDate(plan.first_due_date, paid)
      : null,
  };
}
