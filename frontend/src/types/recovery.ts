export interface Recovery {
  status: string; eligible: boolean; ineligibleReason: string | null; paid: boolean;
  paymentDueAt: string | null; customerCutoffAt: string | null;
  cancelledAt: string | null; cancelledBy: number | null; reasonCode: string | null; note: string | null;
  refunds: { id: number; amount: number; refundedAt: string }[];
  tickets: { id: number; ticketCode: string; status: string }[];
  history: { fromStatus: string; toStatus: string; actorId: number | null; reasonCode: string | null; note: string | null; changedAt: string }[];
}
