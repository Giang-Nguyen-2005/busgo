// Run M29 + M30.2A assertions unchanged, then M30.2B public-information fixtures.
const fs = require('node:fs'), path = require('node:path'), Module = require('node:module');
const base = path.resolve('frontend/tests/m30-2a-browser.cjs');
const info = {
 pickup:{province:'Tỉnh điểm giữa B',district:'Khu vực B'},dropoff:{province:'Tỉnh điểm giữa C',district:null},
 vehicle:{seatCount:40,description:'Cấu hình loại xe do nhà xe khai báo; không xác nhận tiện nghi.'},
 policy:{customerCutoffAt:'2027-01-15T09:00:00Z',fullCancellation:'Hủy toàn bộ có điều kiện khi đặt vé chờ thanh toán hoặc đã xác nhận, chuyến chưa khởi hành/kết thúc, điểm đón chưa đóng và không có khách còn vé đã điểm danh, lên xe hoặc vắng mặt.',partialCancellation:'Hủy một phần cần chuyến còn chờ chạy và giữ lại ít nhất một hành khách; không hủy người đã điểm danh, lên xe hoặc vắng mặt.',modification:'Đổi ghế/chuyến cần chuyến còn chờ chạy và nhà xe hoạt động. Hệ thống kiểm tra ghế/chuyến thay thế và chênh lệch giá.',refund:'Hoàn tiền và thu thêm là mô phỏng, không chuyển tiền ngân hàng.',eligibilityNotice:'Hạn yêu cầu của khách là 6 giờ trước giờ đón theo lịch của chặng đã chọn. Trạng thái đặt vé, hạn thanh toán, nhà xe/tuyến, điểm đón, điểm danh và thay đổi đang chờ có thể hạn chế thao tác. Hệ thống kiểm tra lại khi xác nhận.'}
};
let wrapper = fs.readFileSync(base,'utf8').replace("path.resolve('.tools/m30-2a')","path.resolve('.tools/m30-2b/browser')");
wrapper = wrapper.replace("let source = fs.readFileSync(filename, 'utf8');", `let source = fs.readFileSync(filename, 'utf8');
source = source.replace('const trip = {', ${JSON.stringify('const prebooking = '+JSON.stringify(info)+'; const trip = {')});
source = source.replace('...trip, operations:', '...trip, prebooking: state.missingMetadata ? undefined : prebooking, operations:');`);
const checks = `
 // M30.2B only: all APIs are fixtures, including metadata and policy text.
 for (const guest of [true,false]) {
  await p.goto(origin+tripUrl); await ready(p);
  await p.evaluate(({guest,tokens})=>{if(guest)sessionStorage.removeItem('busgo.auth');else sessionStorage.setItem('busgo.auth',JSON.stringify(tokens));},{guest,tokens});
  for (const width of [1440,834,390,360]) {
   await p.setViewportSize({width,height:900}); await p.goto(origin+tripUrl); await ready(p);
   const panel = p.locator('.prebooking-information');
   await panel.locator('summary').click();
   await panel.getByText('Khu vực: Khu vực B, Tỉnh điểm giữa B',{exact:true}).waitFor();
   await panel.getByText('40 chỗ theo cấu hình loại xe',{exact:true}).waitFor();
   await panel.getByText(/Sơ đồ chuyến: 16 vị trí · Tầng 1, 2/).waitFor();
   await panel.getByText(/không xác nhận vị trí đón/).first().waitFor();
   assert(!(await panel.innerText()).includes('Điểm đón #'));
   await capture(p,'m30b-trip-open-'+(guest?'guest-':'customer-')+width);
   await panel.locator('summary').click();
   await p.getByRole('button',{name:/^Chỗ A01,/}).click();
   const cta=p.getByRole('button',{name:'Tiếp tục',exact:true});
   await cta.click({trial:true});
   assert(await cta.evaluate(e=>{const r=e.getBoundingClientRect();return e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));}));
  }
 }
 check('M30.2B guest/customer information opens at 1440/834/390/360; region is not an exact address, configured/actual counts distinct; seat CTA remains actionable');
 state.missingMetadata=true; await p.goto(origin+tripUrl); await ready(p);
 await p.locator('.prebooking-information summary').click();
 await p.getByText('Chưa có mô tả cấu hình.',{exact:true}).waitFor();
 await p.getByText(/Chưa có bản tóm tắt chính sách áp dụng/).waitFor();
 await capture(p,'m30b-missing-metadata-360');
 state.missingMetadata=false; await p.goto(origin+tripUrl); await ready(p);
 await p.getByRole('button',{name:/^Chỗ A01,/}).click();
 await p.getByRole('button',{name:'Tiếp tục',exact:true}).click(); await p.waitForURL('**/booking'); await ready(p);
 const detailReads=state.calls.filter(c=>c.p==='/trips/7').length;
 for (const width of [1440,834,390,360]) {
  await p.setViewportSize({width,height:900}); await p.goto(origin+'/booking'); await ready(p);
  await p.locator('.prebooking-information summary').click();
  await p.getByText(/Thông tin tham khảo từ bước chọn ghế/).waitFor();
  await p.getByText('40 chỗ theo cấu hình loại xe',{exact:true}).waitFor();
  await capture(p,'m30b-checkout-open-'+width);
  await p.locator('.prebooking-information summary').click();
  await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).click({trial:true});
 }
 assert.equal(state.calls.filter(c=>c.p==='/trips/7').length,detailReads);
 await p.evaluate(()=>{const h=JSON.parse(sessionStorage.getItem('busgo.hold'));delete h.tripInformation;sessionStorage.setItem('busgo.hold',JSON.stringify(h));});
 await p.reload(); await ready(p); await p.locator('.prebooking-information summary').click();
 await p.getByText('Chưa cập nhật tên loại xe.',{exact:true}).waitFor();
 check('M30.2B missing metadata/policy and old hold fallbacks; checkout reuses metadata without duplicate detail API at all viewports');
`;
wrapper = wrapper.replace(' // New M30.2A checks,',checks+'\n // New M30.2A checks,');
const mod = new Module(base,module);mod.filename=base;mod.paths=Module._nodeModulePaths(path.dirname(base));mod._compile(wrapper,base);
