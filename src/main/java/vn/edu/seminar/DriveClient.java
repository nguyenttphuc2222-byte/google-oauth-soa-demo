package vn.edu.seminar;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
@Component
public class DriveClient {
    public record Folder(String id,String name){}
    public record Uploaded(String id,String name,long size,String downloadUrl){
        public Uploaded(String id,String name,long size){this(id,name,size,"");}
        public String url(){return "https://drive.google.com/file/d/"+id+"/view";}
    }
    private final AppSettings app;private final GoogleHttp http;private final ObjectMapper mapper;
    public DriveClient(AppSettings app,GoogleHttp http,ObjectMapper mapper){this.app=app;this.http=http;this.mapper=mapper;}
    public Folder verifyFolder(String id,String t,HttpServletRequest r){
        return verifyFolder(id,app.folderId(),t,r);
    }
    public Folder verifyFolder(String id,String expected,String t,HttpServletRequest r){
        if(!AppSettings.validId(id)||!id.equals(expected))throw new IllegalArgumentException("Hãy chọn đúng thư mục lớp đã cấu hình trong DRIVE_FOLDER_ID.");
        var uri=UriComponentsBuilder.fromUriString(app.google().driveBase()).pathSegment("files",id).queryParam("fields","id,name,mimeType,capabilities(canAddChildren)").queryParam("supportsAllDrives",true).build().encode().toUri();
        var j=http.json(HttpMethod.GET,uri,t,null,"Drive · kiểm tra thư mục Picker",r);
        if(!j.path("mimeType").asText().equals("application/vnd.google-apps.folder")||!j.path("capabilities").path("canAddChildren").asBoolean())throw new IllegalArgumentException("Tài khoản chưa có quyền thêm file vào thư mục.");
        return new Folder(id,j.path("name").asText());
    }
    public Uploaded find(String attemptId,String t,HttpServletRequest r){return find(attemptId,app.folderId(),t,r);}
    public Uploaded find(String attemptId,String folderId,String t,HttpServletRequest r){
        if(!AppSettings.validId(folderId)||!attemptId.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("ID lượt nộp hoặc thư mục không hợp lệ.");
        String q="'"+folderId+"' in parents and trashed = false and appProperties has { key='submissionId' and value='"+attemptId+"' }";
        var uri=UriComponentsBuilder.fromUriString(app.google().driveBase()).pathSegment("files").queryParam("q",q).queryParam("fields","files(id,name,size,webContentLink)").queryParam("pageSize",2).build().encode().toUri();
        var fs=http.json(HttpMethod.GET,uri,t,null,"Drive · tìm file theo lượt nộp",r).path("files");
        if(fs.size()>1)throw new IllegalStateException("Có nhiều file cho cùng lượt nộp. Kiểm tra Drive trước khi tiếp tục.");
        return fs.size()==0?null:new Uploaded(fs.get(0).path("id").asText(),fs.get(0).path("name").asText(),fs.get(0).path("size").asLong(),fs.get(0).path("webContentLink").asText(""));
    }
    public void ensureReader(String fileId,String lecturer,String token,HttpServletRequest request){
        ensureRole(fileId,lecturer,"reader",token,request);
    }
    public com.fasterxml.jackson.databind.JsonNode metadata(String id,String token,HttpServletRequest request){
        if(!AppSettings.validId(id))throw new IllegalArgumentException("Drive ID không hợp lệ.");
        var uri=UriComponentsBuilder.fromUriString(app.google().driveBase()).pathSegment("files",id)
            .queryParam("fields","id,name,mimeType,parents,appProperties,owners(emailAddress),driveId,trashed,writersCanShare,capabilities(canAddChildren,canShare)")
            .queryParam("supportsAllDrives",true).build().encode().toUri();
        return http.json(HttpMethod.GET,uri,token,null,"Drive · kiểm tra tài nguyên đích",request);
    }
    public List<com.fasterxml.jackson.databind.JsonNode> permissions(String id,String token,HttpServletRequest request){
        if(!AppSettings.validId(id))throw new IllegalArgumentException("Drive ID không hợp lệ.");
        var result=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();var visited=new HashSet<String>();String page="";
        do{
            if(!visited.add(page)||visited.size()>100)throw new IllegalStateException("Không đọc được đầy đủ quyền Drive.");
            var builder=UriComponentsBuilder.fromUriString(app.google().driveBase()).pathSegment("files",id,"permissions")
                .queryParam("fields","nextPageToken,permissions(id,type,emailAddress,role,domain,deleted,permissionDetails)")
                .queryParam("pageSize",100).queryParam("supportsAllDrives",true);
            if(!page.isBlank())builder.queryParam("pageToken",page);
            var response=http.json(HttpMethod.GET,builder.build().encode().toUri(),token,null,"Drive · kiểm tra quyền hiện có",request);
            response.path("permissions").forEach(result::add);page=response.path("nextPageToken").asText("");
        }while(!page.isBlank());
        return result;
    }
    public void ensureRole(String id,String address,String role,String token,HttpServletRequest request){
        address=AccountPolicy.email(address);
        if(!Set.of("reader","writer").contains(role))throw new IllegalArgumentException("Vai trò Drive không hợp lệ.");
        for(var permission:permissions(id,token,request)){
            if(!permission.path("type").asText().equals("user")||!address.equalsIgnoreCase(permission.path("emailAddress").asText())||permission.path("deleted").asBoolean())continue;
            String old=permission.path("role").asText();
            if(rank(old)>=rank(role))return; // Never downgrade an owner or a pre-existing stronger grant.
            var uri=UriComponentsBuilder.fromUriString(app.google().driveBase()).pathSegment("files",id,"permissions",permission.path("id").asText())
                .queryParam("supportsAllDrives",true).build().encode().toUri();
            http.json(HttpMethod.PATCH,uri,token,Map.of("role",role),"Drive · bổ sung mức quyền người dùng",request);return;
        }
        var uri=UriComponentsBuilder.fromUriString(app.google().driveBase()).pathSegment("files",id,"permissions")
            .queryParam("sendNotificationEmail",false).queryParam("supportsAllDrives",true).build().encode().toUri();
        http.json(HttpMethod.POST,uri,token,Map.of("type","user","role",role,"emailAddress",address),"Drive · cấp quyền người dùng cụ thể",request);
    }
    private static int rank(String role){return switch(role){case "owner"->60;case "organizer"->50;case "fileOrganizer"->40;case "writer"->30;case "commenter"->20;case "reader"->10;default->0;};}
    public Folder personalFolder(String rootId,String studentKey,String name,String token,HttpServletRequest request){
        if(!AppSettings.validId(rootId)||!studentKey.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Khóa thư mục không hợp lệ.");
        String query="'"+rootId+"' in parents and trashed = false and mimeType = 'application/vnd.google-apps.folder' and appProperties has { key='driveAccessStudent' and value='"+studentKey+"' }";
        var uri=UriComponentsBuilder.fromUriString(app.google().driveBase()).pathSegment("files").queryParam("q",query).queryParam("fields","files(id,name)").queryParam("pageSize",2).build().encode().toUri();
        var found=http.json(HttpMethod.GET,uri,token,null,"Drive · tìm thư mục riêng của sinh viên",request).path("files");
        if(found.size()>1)throw new IllegalStateException("Có nhiều thư mục cho sinh viên; chủ thư mục cần kiểm tra trước khi tiếp tục.");
        if(found.size()==1)return new Folder(found.get(0).path("id").asText(),found.get(0).path("name").asText());
        var create=UriComponentsBuilder.fromUriString(app.google().driveBase()).pathSegment("files").queryParam("fields","id,name").build().encode().toUri();
        var metadata=Map.of("name",name,"mimeType","application/vnd.google-apps.folder","parents",List.of(rootId),
            "writersCanShare",false,"appProperties",Map.of("driveAccessStudent",studentKey,"driveAccessRoot",rootId));
        var created=http.json(HttpMethod.POST,create,token,metadata,"Drive · tạo thư mục riêng của sinh viên",request);
        if(!AppSettings.validId(created.path("id").asText()))throw new GoogleApiException(0,"Drive · phản hồi tạo thư mục");
        return new Folder(created.path("id").asText(),created.path("name").asText(name));
    }
    public void requireParent(String fileId,String folderId,String token,HttpServletRequest request){
        var metadata=metadata(fileId,token,request);boolean match=false;
        for(var parent:metadata.path("parents"))match|=parent.asText().equals(folderId);
        if(!match)throw new IllegalArgumentException("File của lượt nộp không nằm trong thư mục đích hiện tại. Tạo lượt nộp mới nếu đã đổi cấu hình thư mục.");
    }
    public Uploaded upload(String id,String sub,UploadValidator.Validated file,String t,HttpServletRequest r){return upload(id,sub,file,app.folderId(),t,r);}
    public Uploaded upload(String id,String sub,UploadValidator.Validated file,String folderId,String t,HttpServletRequest r){
        if(!AppSettings.validId(folderId))throw new IllegalArgumentException("Thư mục đích không hợp lệ.");
        try{String boundary="seminar_"+UUID.randomUUID().toString().replace("-","");var out=new ByteArrayOutputStream();
            var metadata=Map.of("name",file.name(),"mimeType",file.mime(),"parents",List.of(folderId),"appProperties",Map.of("submissionId",id,"studentSub",sub));
            out.write(("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(mapper.writeValueAsBytes(metadata));
            out.write(("\r\n--"+boundary+"\r\nContent-Type: "+file.mime()+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(file.bytes());out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            URI uri=UriComponentsBuilder.fromUriString(app.google().uploadBase()).pathSegment("files").queryParam("uploadType","multipart").queryParam("fields","id,name,size,webContentLink").queryParam("supportsAllDrives",true).build().encode().toUri();
            var ans=http.call(HttpMethod.POST,uri,t,out.toByteArray(),MediaType.parseMediaType("multipart/related; boundary="+boundary),"Drive · upload minh chứng",r);
            if(!AppSettings.validId(ans.path("id").asText()))throw new GoogleApiException(0,"Drive · phản hồi upload");
            return new Uploaded(ans.path("id").asText(),ans.path("name").asText(file.name()),ans.path("size").asLong(file.bytes().length),ans.path("webContentLink").asText(""));
        }catch(java.io.IOException e){throw new IllegalStateException("Không tạo được upload multipart.");}
    }
}
