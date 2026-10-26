package vn.edu.seminar;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class AuditTape {
    public record Event(String at,String action,String result){}
    @SuppressWarnings("unchecked") public List<Event> events(HttpServletRequest r){var s=r.getSession();var a=(List<Event>)s.getAttribute("seminar.audit");if(a==null){a=new ArrayList<>();s.setAttribute("seminar.audit",a);}return a;}
    public void add(HttpServletRequest r,String action,String result){synchronized(r.getSession()){var a=events(r);a.add(0,new Event(Instant.now().toString(),action,result));while(a.size()>20)a.remove(a.size()-1);}}
}
