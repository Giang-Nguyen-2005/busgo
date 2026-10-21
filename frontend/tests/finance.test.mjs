import test from 'node:test';
import assert from 'node:assert/strict';
import { financeLabel, financeMoney, financeDate, validFinancePeriod } from '../src/features/finance/financeModel.ts';
import { register } from 'node:module';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { canAccessOperatorPath, operatorNavigation } = await import('../src/features/auth/access.ts');
test('financial uncertainty is never formatted as zero or bank confirmation', () => {
  assert.equal(financeMoney(null), 'Chưa xác định');
  assert.match(financeMoney(0), /0/);
  assert.equal(financeLabel('UNKNOWN_OR_UNVERIFIED'), 'Chưa xác minh bên thu');
  assert.match(financeLabel('PLATFORM_MEDIATED_SIMULATED'), /mô phỏng/);
  assert.match(financeLabel('OPERATOR_RECORDED_SIMULATED'), /mô phỏng/);
});
test('financial period bounds and Vietnam date display', () => {
  assert.equal(validFinancePeriod('2026-10-01','2026-10-21'), true);
  assert.equal(validFinancePeriod('2026-10-21','2026-10-01'), false);
  assert.equal(validFinancePeriod('2026-01-01','2026-12-31'), false);
  assert.equal(validFinancePeriod('',''), false);
  assert.match(financeDate('2026-10-20T18:00:00Z'), /21\/10\/2026/);
});
test('operator finance navigation and direct routes exclude staff and mixed system roles', () => {
  assert.equal(canAccessOperatorPath(['OPERATOR_ADMIN'],'/operator/finance'), true);
  for(const roles of [['OPERATOR_STAFF'],['CUSTOMER'],['SYSTEM_ADMIN','OPERATOR_ADMIN']]) {
    assert.equal(canAccessOperatorPath(roles,'/operator/finance'),false);
    assert.equal(operatorNavigation(roles).some(([path])=>path==='/finance'),false);
  }
});
