package vn.edu.seminar;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
@Component
public class GoogleHttp {
    private final RestClient http;private final ObjectMapper mapper;private final AuditTape tape;
    public GoogleHttp(ObjectMapper mapper,AuditTape tape){this.mapper=mapper;this.tape=tape;var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());factory.setReadTimeout(Duration.ofSeconds(45));http=RestClient.builder().requestFactory(factory).build();}
    public JsonNode call(HttpMethod method,URI uri,String token,Object body,MediaType type,String operation,HttpServletRequest request){
        try{var spec=http.method(method).uri(uri);if(token!=null)spec.header(HttpHeaders.AUTHORIZATION,"Bearer "+token);if(type!=null)spec.contentType(type);
            var ans=(body==null?spec:spec.body(body)).retrieve().toEntity(String.class);tape.add(request,operation,"HTTP "+ans.getStatusCode().value());
            return ans.getBody()==null||ans.getBody().isBlank()?mapper.createObjectNode():mapper.readTree(ans.getBody());
        }catch(RestClientResponseException e){tape.add(request,operation,"HTTP "+e.getStatusCode().value());throw new GoogleApiException(e.getStatusCode().value(),operation);
        }catch(RestClientException|java.io.IOException e){tape.add(request,operation,"NETWORK");throw new GoogleApiException(0,operation);}
    }
    public JsonNode json(HttpMethod method,URI uri,String token,Object body,String op,HttpServletRequest r){return call(method,uri,token,body,MediaType.APPLICATION_JSON,op,r);}
}
