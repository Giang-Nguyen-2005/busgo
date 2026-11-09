// Match the original catalogue copy exactly so subsequent operator edits are displayed verbatim.
const introductions = new Map<string,string>([
  ["An Phú Express là nhà xe trong bộ dữ liệu demo BusGo, giới thiệu các hành trình mẫu từ TP. Hồ Chí Minh đến Đà Lạt, Nha Trang và miền Nam. Đội xe mẫu gồm giường nằm 34 chỗ và ghế ngồi 40 chỗ. Lịch, giá và chỗ trống cần được kiểm tra theo ngày; các tuyến dự kiến chưa có lịch không phải dịch vụ đang mở bán. Đặt vé và thanh toán đều là mô phỏng, không tiếp nhận yêu cầu vận chuyển thật; nội dung không chứng nhận pháp lý hay liên hệ vận hành.",
   "Bạn có thể tìm các chuyến An Phú Express từ TP. Hồ Chí Minh đi Đà Lạt, Nha Trang và Phan Thiết. Đội xe gồm xe giường nằm 34 chỗ và xe ghế ngồi 40 chỗ."],
  ["Minh Thành Limousine thuộc bộ dữ liệu demo BusGo, với xe mẫu limousine 22 chỗ, giường nằm 34 chỗ và ghế ngồi 40 chỗ. Nội dung giới thiệu các hành trình dự kiến giữa TP. Hồ Chí Minh, Đà Lạt, Vũng Tàu và duyên hải miền Trung. Chỉ những chuyến có lịch và giá hợp lệ mới có thể đặt trong bản demo. Đây là dữ liệu mô phỏng, không xác nhận tiện nghi VIP, tư cách pháp lý hay dịch vụ vận chuyển thật; thanh toán không phát sinh tiền thật.",
   "Minh Thành Limousine có các chuyến giữa TP. Hồ Chí Minh và Đà Lạt, Vũng Tàu, cùng tuyến Đà Nẵng – Huế. Chọn ngày đi để xem loại xe và giờ khởi hành."],
  ["Tây Nguyên Travel là nhà xe mẫu phục vụ trải nghiệm thử BusGo. Đội xe mô phỏng gồm ghế ngồi 40 chỗ, limousine 22 chỗ và giường nằm 34 chỗ, chuẩn bị cho hành trình cao nguyên và duyên hải như Buôn Ma Thuột, Pleiku, Nha Trang, Quy Nhơn. Tuyến dự kiến không đồng nghĩa có chuyến mỗi ngày; vui lòng kiểm tra dữ liệu khi chọn hành trình. Nội dung và liên hệ chỉ dành cho demo, không chứng thực doanh nghiệp hoặc tiếp nhận vận chuyển thật. Đặt vé và thanh toán đều được mô phỏng.",
   "Tây Nguyên Travel giới thiệu các tuyến Buôn Ma Thuột – Pleiku và Nha Trang – Quy Nhơn. Chọn hành trình để xem chuyến còn chỗ trong ngày."],
]);
export function profileCopy(description?: string | null) {
  const text=description?.trim() || "";
  return introductions.get(text) ?? text;
}

const offerCodes = new Set(["M31-AP-FIX20","M31-AP-MIN50","M31-MT-PCT10","M31-MT-FUTURE10","M31-TN-PRIVATE15","M31-TN-PAUSED10"]);
export function offerTitle(name:string, code:string, type:string, value:number) {
  if (!offerCodes.has(code) || name !== `DEMO · ${code}`) return name;
  return type === "PERCENT" ? `Giảm ${value}%` : `Giảm ${new Intl.NumberFormat("vi-VN").format(value)}đ`;
}
export function offerDescription(description?:string|null) {
  return description === "Chỉ mô phỏng trong DB test cô lập; không phải khuyến mãi thực tế. Điều kiện và hạn mức hiển thị kèm mã." ? "" : description;
}
export function photoDescription(alt:string, type:string) {
  return alt.startsWith("[DEMO M31]") ? `Ảnh minh họa loại xe ${type}` : alt || type;
}
export function employeeLabel(value:string) {
  const match=/^Nhân sự demo (AP|MT|TN) (DRIVER|ATTENDANT)$/.exec(value);
  if (!match) return value;
  const companies:Record<string,string>={AP:"An Phú Express",MT:"Minh Thành Limousine",TN:"Tây Nguyên Travel"};
  return `${match[2]==="DRIVER"?"Tài xế":"Phụ xe"} · ${companies[match[1]]}`;
}
export const contactLabel=(value?:string|null)=>!value || value==="DEMO-NOT-DIALABLE" ? "Chưa cập nhật" : value;
