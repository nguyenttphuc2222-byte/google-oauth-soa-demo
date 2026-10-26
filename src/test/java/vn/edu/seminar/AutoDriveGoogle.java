package vn.edu.seminar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.*;

/** Drive ACL simulator for the automatic-access tests, never packaged in production. */
class AutoDriveGoogle extends FakeGoogle {
    static final String OWNER="523h0080@student.tdtu.edu.vn", ROOT="folder_123456";
    final Map<String,Map<String,Object>> nodes=new ConcurrentHashMap<>();
    final Map<String,List<Map<String,Object>>> acl=new ConcurrentHashMap<>();
    final List<Map<String,Object>> mutations=new ArrayList<>();
    final AtomicInteger foldersCreated=new AtomicInteger();
    final Set<String> pickerRequired=new HashSet<>(),picked=new HashSet<>();
    boolean denySharing,failAfterFolder,rejectRefresh,ownerCannotShare;
    int permissionPageSize;
    private int permissionCount;
    AutoDriveGoogle()throws Exception{super();reset();}
    @Override public void reset(){
        super.reset();if(nodes==null)return;nodes.clear();acl.clear();mutations.clear();foldersCreated.set(0);
        pickerRequired.clear();picked.clear();denySharing=false;failAfterFolder=false;rejectRefresh=false;ownerCannotShare=false;permissionPageSize=0;permissionCount=0;
        var root=new HashMap<String,Object>();root.put("id",ROOT);root.put("name","Seminar - Minh chứng");root.put("mimeType","application/vnd.google-apps.folder");
        root.put("owners",List.of(Map.of("emailAddress",OWNER)));root.put("parents",List.of());root.put("appProperties",Map.of());root.put("writersCanShare",false);nodes.put(ROOT,root);
        acl.put(ROOT,new ArrayList<>(List.of(permission(OWNER,"owner"))));
    }
    Map<String,Object> permission(String email,String role){return new HashMap<>(Map.of("id","permission_"+(++permissionCount),"type","user","emailAddress",email,"role",role));}
    void allow(String id,String email,String role){acl.computeIfAbsent(id,unused->new ArrayList<>()).add(permission(email,role));}
    int role(String id,String email){
        int result=0;
        for(var p:acl.getOrDefault(id,List.of()))if(email.equalsIgnoreCase(Objects.toString(p.get("emailAddress"),"")))result=Math.max(result,rank(Objects.toString(p.get("role"),"")));
        var node=nodes.get(id);if(node!=null)for(Object parent:(List<?>)node.getOrDefault("parents",List.of()))result=Math.max(result,Math.min(30,role(parent.toString(),email)));
        return result;
    }
    static int rank(String role){return switch(role){case "owner"->60;case "writer"->30;case "commenter"->20;case "reader"->10;default->0;};}
    @Override public MockResponse dispatch(RecordedRequest request){try{
        String path=request.getRequestUrl().encodedPath();
        if(path.equals("/test/identity")){
            nextEmail=Objects.toString(request.getRequestUrl().queryParameter("email"),OWNER);nextSub=nextEmail;nextHd="tdtu.edu.vn";
            if("true".equals(request.getRequestUrl().queryParameter("picker")))pickerRequired.add(nextEmail);
            return json(200,Map.of("ok",true));
        }
        if(path.equals("/test/picker/select")){
            picked.add(request.getRequestUrl().queryParameter("email")+":"+request.getRequestUrl().queryParameter("folderId"));return json(200,Map.of("ok",true));
        }
        if(path.equals("/test/drive-state"))return json(200,Map.of("acl",acl,"folders",foldersCreated.get()));
        if(path.equals("/token")&&rejectRefresh&&request.getBody().clone().readUtf8().contains("refresh_token"))return json(400,Map.of("error","invalid_grant"));
        if(!path.startsWith("/drive/")&&!path.startsWith("/upload/"))return super.dispatch(request);
        var actor=access.get(bearer(request));if(actor==null)return json(401,Map.of("error","invalid_token"));
        if(!actor.scope().contains(Scopes.DRIVE))return json(403,Map.of("error","missing_scope"));
        var parts=request.getRequestUrl().pathSegments();int fileIndex=parts.indexOf("files");
        String id=parts.size()>fileIndex+1?parts.get(fileIndex+1):null;
        String method=request.getMethod();
        if(id!=null&&!nodes.containsKey(id))return json(404,Map.of("error","not_found"));
        if(id!=null&&role(id,actor.email())==0)return json(403,Map.of("error","no_acl"));
        if(id!=null&&parts.contains("permissions")){
            if(method.equals("GET")){
                int start=Integer.parseInt(Objects.toString(request.getRequestUrl().queryParameter("pageToken"),"0"));
                var list=acl.getOrDefault(id,List.of());int end=permissionPageSize==0?list.size():Math.min(list.size(),start+permissionPageSize);
                var response=new HashMap<String,Object>();response.put("permissions",list.subList(start,end));if(end<list.size())response.put("nextPageToken",String.valueOf(end));return json(200,response);
            }
            if(denySharing||role(id,actor.email())<30)return json(403,Map.of("error","sharing_blocked"));
            var body=mapper.readTree(request.getBody().readUtf8());String wanted=body.path("role").asText();
            if(method.equals("PATCH")){
                String pid=parts.get(parts.size()-1);
                for(var permission:acl.get(id))if(pid.equals(permission.get("id"))){permission.put("role",wanted);mutations.add(Map.of("fileId",id,"email",permission.get("emailAddress"),"role",wanted,"method","PATCH","type","user"));return json(200,permission);}
                return json(404,Map.of("error","no_permission"));
            }
            String email=body.path("emailAddress").asText();var permission=permission(email,wanted);acl.get(id).add(permission);
            mutations.add(Map.of("fileId",id,"email",email,"role",wanted,"method","POST","type",body.path("type").asText()));
            if(wanted.equals("reader"))viewers.put(id,email);return json(200,permission);
        }
        if(id!=null){
            if(nodes.get(id).get("mimeType").equals("application/vnd.google-apps.folder")&&pickerRequired.contains(actor.email())&&!actor.email().equals(OWNER)&&!picked.contains(actor.email()+":"+id))return json(404,Map.of("error","picker_required"));
            var result=new HashMap<>(nodes.get(id));int rank=role(id,actor.email());
            result.put("capabilities",Map.of("canAddChildren",rank>=30,"canShare",rank>=60&&!ownerCannotShare));return json(200,result);
        }
        if(path.startsWith("/upload/")){
            String body=request.getBody().readUtf8();int start=body.indexOf("\r\n\r\n")+4,end=body.indexOf("\r\n--",start);
            var metadata=mapper.readTree(body.substring(start,end));String parent=metadata.path("parents").get(0).asText();
            if(role(parent,actor.email())<30)return json(403,Map.of("error","no_write"));
            String fileId="file_"+UUID.randomUUID().toString().replace("-","");uploads.incrementAndGet();
            var node=new HashMap<String,Object>();node.put("id",fileId);node.put("name",metadata.path("name").asText());node.put("mimeType",metadata.path("mimeType").asText());
            node.put("parents",List.of(parent));node.put("owners",List.of(Map.of("emailAddress",actor.email())));node.put("appProperties",mapper.convertValue(metadata.path("appProperties"),Map.class));
            node.put("size",1024);node.put("webContentLink","https://drive.google.com/uc?export=download&id="+fileId);nodes.put(fileId,node);acl.put(fileId,new ArrayList<>(List.of(permission(actor.email(),"owner"))));
            files.put(metadata.path("appProperties").path("submissionId").asText(),node);return json(200,node);
        }
        if(method.equals("GET")){
            String query=Objects.toString(request.getRequestUrl().queryParameter("q"),"");boolean folders=query.contains("driveAccessStudent");
            var found=new ArrayList<Map<String,Object>>();
            for(var node:nodes.values()){
                var properties=(Map<?,?>)node.getOrDefault("appProperties",Map.of());String value=Objects.toString(properties.get(folders?"driveAccessStudent":"submissionId"),"");
                boolean parent=false;for(var p:(List<?>)node.getOrDefault("parents",List.of()))parent|=query.contains("'"+p+"' in parents");
                if(parent&&!value.isBlank()&&query.contains(value)&&role(node.get("id").toString(),actor.email())>0)found.add(node);
            }
            return json(200,Map.of("files",found));
        }
        var body=mapper.readTree(request.getBody().readUtf8());String parent=body.path("parents").get(0).asText();
        if(!actor.email().equals(OWNER)||!ROOT.equals(parent))return json(403,Map.of("error","folder_scope"));
        String folder="folder_auto_"+foldersCreated.incrementAndGet();
        var node=new HashMap<String,Object>();node.put("id",folder);node.put("name",body.path("name").asText());node.put("mimeType",body.path("mimeType").asText());
        node.put("parents",List.of(parent));node.put("owners",List.of(Map.of("emailAddress",OWNER)));node.put("appProperties",mapper.convertValue(body.path("appProperties"),Map.class));node.put("writersCanShare",body.path("writersCanShare").asBoolean());
        nodes.put(folder,node);acl.put(folder,new ArrayList<>(List.of(permission(OWNER,"owner"))));
        if(failAfterFolder){failAfterFolder=false;return json(503,Map.of("error","after_create"));}return json(200,node);
    }catch(Exception exception){return json(500,Map.of("error",exception.getClass().getSimpleName()));}}
}
