package vn.edu.seminar;
import jakarta.servlet.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
@ControllerAdvice
public class ErrorAdvice {
    private final ViewAdvice views;private final ObjectMapper mapper;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ErrorAdvice.class);
    public ErrorAdvice(ViewAdvice views,ObjectMapper mapper){this.views=views;this.mapper=mapper;}
    @ExceptionHandler(Exception.class) public ModelAndView problem(Exception e,HttpServletRequest r,HttpServletResponse response)throws java.io.IOException{
        int status=e instanceof AccessDeniedException||e instanceof Scopes.Missing?403:e instanceof org.springframework.web.servlet.NoHandlerFoundException||e instanceof org.springframework.web.servlet.resource.NoResourceFoundException?404:e instanceof MaxUploadSizeExceededException?413:e instanceof GoogleApiException?502:e instanceof IllegalArgumentException||e instanceof IllegalStateException?400:500;
        String message=status==500?"Không hoàn tất được thao tác. Tải lại trang hoặc xem terminal để kiểm tra app.":e.getMessage();
        if(status==500)log.error("Lỗi nội bộ tại {} {}",r.getMethod(),r.getRequestURI(),e);
        if(status==413)message="File hoặc yêu cầu vượt giới hạn. Chọn file nhỏ hơn giới hạn cấu hình.";
        if(status==404)message="Không tìm thấy trang được yêu cầu.";
        if("XMLHttpRequest".equals(r.getHeader("X-Requested-With"))){response.setStatus(status);response.setContentType("application/json;charset=UTF-8");mapper.writeValue(response.getWriter(),Map.of("error",message));return null;}
        var view=new ModelAndView("problem");view.setStatus(HttpStatus.valueOf(status));var model=new ExtendedModelMap();views.common(model,SecurityContextHolder.getContext().getAuthentication(),r);view.addAllObjects(model);
        view.addObject("errorTitle",status==403?"Cần kiểm tra quyền truy cập":"Chưa hoàn tất thao tác");view.addObject("errorMessage",message);
        if(e instanceof Scopes.Missing m)view.addObject("neededPurpose",m.purpose);return view;
    }
}
