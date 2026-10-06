export function notificationTarget(value: string | null, operator: boolean): string | null {
  const pattern = operator ? /^\/operator\/bookings\/[1-9]\d*$/ : /^\/my-bookings\/[1-9]\d*$/;
  return value && pattern.test(value) ? value : null;
}
