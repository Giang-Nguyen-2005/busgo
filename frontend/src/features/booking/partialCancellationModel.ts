type Candidate = { bookingItemId: number; cancelled: boolean; eligibility: { allowed: boolean } };

export function canReviewPartial(items: Candidate[], selected: number[]) {
  const active = items.filter(i => !i.cancelled);
  return selected.length > 0 && selected.length < active.length
    && new Set(selected).size === selected.length
    && selected.every(id => active.some(i => i.bookingItemId === id && i.eligibility.allowed));
}
