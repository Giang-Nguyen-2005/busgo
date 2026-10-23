import test from 'node:test';
import assert from 'node:assert/strict';
import {register} from 'node:module';
register('./helpers/tsx-loader.mjs',import.meta.url);
const {createElement}=await import('react');
const {renderToStaticMarkup}=await import('react-dom/server');
const {Summary}=await import('../src/features/finance/Finance.tsx');
const totals={collections:200,refunds:20,netCollections:180,commissionAccrual:10,commissionReversal:1,netCommission:9,operatorPayable:100,platformReceivable:8,unknownAmount:null,unpricedAmount:null,grossContext:300,discountContext:30,eventCount:4,sourceCount:6,missingCount:7,sourceCollections:900,sourceRefunds:40,unknownCount:2,unpricedCount:3};
const render=()=>renderToStaticMarkup(createElement(Summary,{value:{period:{from:'2026-10-01',to:'2026-10-23'},operatorName:'Test',reconciliationStatus:'NOT_RECONCILED',totals}}));
test('reconciliation distinguishes source counts, ledger entries and authoritative missing count',()=>{const s=render();assert.match(s,/6 sự kiện nguồn/);assert.match(s,/4 bút toán/);assert.match(s,/7 sự kiện chưa ghi sổ/);assert.match(s,/Nguồn thanh toán/);assert.match(s,/Sổ đã ghi/);});
test('unknown amounts stay unknown and liabilities remain internal',()=>{const s=render();assert.match(s,/Chưa xác định/);assert.match(s,/Phải trả nhà xe \(nội bộ\)/);assert.match(s,/Chưa xác nhận thanh toán thực tế/);});
test('full original metrics remain in keyboard-operable native disclosure',()=>{const s=render();assert.match(s,/<details class="finance-metrics"><summary>/);for(const label of ['Giá vé gốc','Giảm giá nhà xe','Hoa hồng phát sinh','Hoa hồng hoàn lại','Giá trị ròng chưa rõ bên thu','Giá trị ròng chưa tính hoa hồng'])assert(s.includes(label));});
const {QueryClient,QueryClientProvider}=await import('@tanstack/react-query');
const {MemoryRouter}=await import('react-router-dom');
const {PlatformDashboard}=await import('../src/features/onboarding/Onboarding.tsx');
function dashboard(disputes){const client=new QueryClient({defaultOptions:{queries:{retry:false}}});client.setQueryData(['admin','dashboard'],{SUBMITTED:3,UNDER_REVIEW:2,ONBOARDING:1,ACTIVE:7,SUSPENDED:4,ACTIVE_ROUTES:12,UPCOMING_TRIPS:24});if(disputes)client.setQueryData(['admin','dispute-counters'],disputes);return renderToStaticMarkup(createElement(QueryClientProvider,{client},createElement(MemoryRouter,null,createElement(PlatformDashboard))));}
test('platform dashboard separates applications and operator populations',()=>{const s=dashboard([]);for(const label of ['Đơn đăng ký đang xử lý','Chờ hoàn tất đăng ký','Trạng thái nhà xe','Nguồn cung nền tảng'])assert(s.includes(label));assert.match(s,/không phải số đơn đăng ký/);assert.match(s,/Chưa xác định/);});
test('independent dispute queue loading does not suppress known platform counts',()=>{const s=dashboard();assert.match(s,/Tranh chấp cần chú ý/);assert.match(s,/Đang tải/);assert.match(s,/<dd>7<\/dd>/);assert.match(s,/href="\/admin\/disputes"/);});
