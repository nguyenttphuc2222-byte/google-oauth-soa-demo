package vn.edu.seminar;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
@Component
public class UploadValidator {
    public record Validated(String name,String mime,byte[] bytes){}
    private static final String TYPES = "PDF, DOC, DOCX, PPT, PPTX hoặc ZIP";
    private static final int MAX_ENTRIES = 8000;
    private static final long MAX_EXPANDED = 100 * 1048576L;
    private static final byte[] OLE_HEADER = {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
        (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1};
    private final AppSettings app;
    public UploadValidator(AppSettings app){this.app=app;}
    public Validated validate(MultipartFile file){
        if(file==null||file.isEmpty()||file.getSize()>app.maxUploadMb()*1048576L)throw new IllegalArgumentException("Chọn file "+TYPES+", tối đa "+app.maxUploadMb()+" MB.");
        String name=Objects.toString(file.getOriginalFilename(),"file").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","_");
        if(name.isBlank()||name.length()>180)throw new IllegalArgumentException("Tên file không hợp lệ hoặc quá dài.");
        try{byte[] bytes=file.getBytes();String lower=name.toLowerCase(Locale.ROOT);String mime;
            if(lower.endsWith(".pdf")&&new String(bytes,0,Math.min(5,bytes.length),StandardCharsets.US_ASCII).equals("%PDF-"))mime="application/pdf";
            else if(lower.endsWith(".doc")&&ole(bytes))mime="application/msword";
            else if(lower.endsWith(".ppt")&&ole(bytes))mime="application/vnd.ms-powerpoint";
            else if(lower.endsWith(".docx")&&validArchive(bytes,"word/document.xml"))mime="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            else if(lower.endsWith(".pptx")&&validArchive(bytes,"ppt/presentation.xml"))mime="application/vnd.openxmlformats-officedocument.presentationml.presentation";
            else if(lower.endsWith(".zip")&&validArchive(bytes,null))mime="application/zip";
            else throw new IllegalArgumentException("Nội dung file không đúng "+TYPES+". Không chỉ đổi phần mở rộng.");
            return new Validated(name,mime,bytes);
        }catch(IOException e){throw new IllegalArgumentException("Không đọc được file. Với Word/PowerPoint/ZIP, hãy chọn file hợp lệ, không mã hóa hoặc đặt mật khẩu.");}
    }
    private boolean ole(byte[] bytes) {
        // The binary DOC/PPT formats share the OLE container signature.
        return bytes.length >= 512 && Arrays.equals(Arrays.copyOf(bytes, 8), OLE_HEADER);
    }
    private boolean validArchive(byte[] bytes,String officePart)throws IOException{
        // Require a central directory. Entry contents are checked, never extracted to disk.
        var temporary=Files.createTempFile("seminar-upload-",".zip");
        try{
            Files.write(temporary,bytes);
            // CP437 supports legacy ZIP filenames; the UTF-8 flag still takes precedence.
            try(var zip=new ZipFile(temporary.toFile(),Charset.forName("CP437"))){
                boolean types=false,document=false,hasFile=false;int count=0;long total=0;byte[] buffer=new byte[8192];
                var entries=zip.entries();
                while(entries.hasMoreElements()){
                    var entry=entries.nextElement();
                    if(++count>MAX_ENTRIES)throw new IllegalArgumentException("File nén có quá nhiều mục (tối đa 8.000).");
                    if(entry.isDirectory())continue;
                    hasFile=true;types|=entry.getName().equals("[Content_Types].xml");document|=entry.getName().equals(officePart);
                    if(entry.getSize()>MAX_EXPANDED-total)throw new IllegalArgumentException("Nội dung giải nén vượt giới hạn 100 MB.");
                    var crc=new CRC32();long size=0;
                    try(var input=zip.getInputStream(entry)){
                        int n;
                        while((n=input.read(buffer))!=-1){
                            size+=n;total+=n;
                            if(total>MAX_EXPANDED)throw new IllegalArgumentException("Nội dung giải nén vượt giới hạn 100 MB.");
                            crc.update(buffer,0,n);
                        }
                    }
                    if(size!=entry.getSize()||crc.getValue()!=entry.getCrc())throw new IOException("Invalid ZIP entry size or checksum");
                }
                return hasFile&&(officePart==null||(types&&document));
            }
        }finally{Files.deleteIfExists(temporary);}
    }
}
