export const customerFaq = [
  ["Làm sao tìm chuyến phù hợp?", "Chọn điểm đón, điểm trả và ngày khởi hành, sau đó lọc theo giờ đi, nhà xe hoặc giá vé. Mỗi tuyến có lịch riêng; kết quả tìm kiếm cho biết giá và chỗ trống tại thời điểm kiểm tra."],
  ["Chọn ghế đã giữ được chỗ chưa?", "Chưa. Sau khi chọn ghế, hãy đăng nhập và tiếp tục để hệ thống xác nhận giữ chỗ. Bạn sẽ thấy thời gian còn lại để hoàn tất đặt vé; hết hạn cần chọn lại."],
  ["Cần cung cấp gì khi đặt vé?", "Bạn có thể đặt tối đa 5 chỗ mỗi lần. Chuẩn bị tên, số điện thoại và email của người liên hệ; bước này chưa thu thập tên riêng từng hành khách. Kiểm tra hành trình và tổng tiền trước khi xác nhận."],
  ["Thanh toán có trừ tiền thật không?", "Không. BusGo hiện dùng thanh toán và hoàn tiền mô phỏng, không chuyển khoản hay thu tiền thật. Vé điện tử được cấp sau khi thanh toán mô phỏng thành công."],
  ["Xem vé, đổi hoặc hủy ở đâu?", "Mở Vé của tôi, chọn đặt vé cần xem rồi vào phần chi tiết. Tại đây bạn có thể xem vé điện tử và kiểm tra điều kiện đổi ghế, đổi chuyến hoặc hủy. Các lựa chọn khả dụng phụ thuộc vào trạng thái đặt vé và chuyến đi."],
  ["Tôi cần hỗ trợ cho đặt vé thì làm thế nào?", "Vào chi tiết đặt vé trong Vé của tôi để gửi yêu cầu hỗ trợ. Theo dõi phản hồi và trao đổi tiếp trong tài khoản. Gửi yêu cầu không tự động hủy vé hoặc hoàn tiền."],
] as const;
export function CustomerFaq() {
  return <section id="customer-faq" className="customer-faq card" aria-labelledby="customer-faq-heading"><h2 id="customer-faq-heading">Câu hỏi thường gặp</h2>{customerFaq.map(([question, answer]) => <details key={question}><summary>{question}</summary><p>{answer}</p></details>)}</section>;
}
