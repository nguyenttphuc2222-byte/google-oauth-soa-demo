package vn.edu.seminar;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
@Component("ui")
public class UiTools {
    private final ZoneId zone;
    public UiTools(AppSettings app){zone=ZoneId.of(app.displayTimezone());}
    public String time(String v){try{return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone).format(Instant.parse(v));}catch(Exception e){return v;}}
    public String initials(String s){if(s==null||s.isBlank())return "U";var a=s.trim().split("\\s+");return (""+a[0].charAt(0)+(a.length>1?a[a.length-1].charAt(0):' ')).trim().toUpperCase();}
}
