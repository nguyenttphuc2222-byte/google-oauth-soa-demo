package vn.edu.seminar;
public record Review(String id, String submissionId, String reviewedAt, String reviewerSub,
        String reviewerEmail, String decision, String comment) {
    public String decisionLabel() { return "APPROVED".equals(decision) ? "Hợp lệ" : "Không hợp lệ"; }
    public String statusClass() { return "APPROVED".equals(decision) ? "approved" : "rejected"; }
}
