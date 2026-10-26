package vn.edu.seminar;
public class GoogleApiException extends RuntimeException {
    public final int status;
    public GoogleApiException(int status,String operation){super(operation+": "+(status==0?"lỗi kết nối; kết quả có thể chưa xác định":"Google trả HTTP "+status+". Kiểm tra API đã bật, scope, quyền chia sẻ và protected range."));this.status=status;}
    public boolean uncertain(){return status==0||status>=500;}
}
