package vn.edu.seminar;
public record MailNotice(String id, String submissionId, String kind, String senderSub,
        String senderEmail, String toEmail, String ccEmail, String status, String messageId, String error) {
    public boolean sent() { return "SENT".equals(status); }
    public boolean uncertain() { return "UNKNOWN".equals(status) || "SENDING".equals(status); }
    public String label() {
        return switch (status) {
            case "SENT" -> "Đã gửi email";
            case "FAILED" -> "Gửi email lỗi";
            case "UNKNOWN", "SENDING" -> "Cần kiểm tra thư đã gửi";
            default -> "Chờ gửi email";
        };
    }
    public String kindLabel() { return "SUBMISSION".equals(kind) ? "Thông báo nộp bài" : "Thông báo kết quả"; }
}
