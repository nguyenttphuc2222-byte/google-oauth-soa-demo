package vn.edu.seminar;
import java.util.*;
import org.springframework.boot.SpringApplication;
/** Developer-only local browser fixture; never packaged in the executable JAR. */
public class BrowserSmokeServer {
    public static void main(String[] args)throws Exception{var google=new FakeGoogle();var properties=google.properties();properties.put("server.port",18080);
        properties.put("app.base-url","http://localhost:18080");
        String database=System.getProperty("seminar.browser.database");if(database!=null)properties.put("spring.datasource.url",database);
        var options=properties.entrySet().stream().map(e->"--"+e.getKey()+"="+e.getValue()).toArray(String[]::new);var context=SpringApplication.run(SeminarApplication.class,options);
        System.out.println("UI_FAKE_GOOGLE="+google.base());Runtime.getRuntime().addShutdownHook(new Thread(()->{context.close();try{google.close();}catch(Exception ignored){}}));
    }
}
