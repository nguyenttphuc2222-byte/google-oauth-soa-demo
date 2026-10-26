package vn.edu.seminar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@SpringBootApplication
@EnableConfigurationProperties({AppSettings.class, DriveAccessSettings.class})
public class SeminarApplication {
    public static void main(String[] args){SpringApplication.run(SeminarApplication.class,args);}
}
