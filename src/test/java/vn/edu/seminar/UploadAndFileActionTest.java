package vn.edu.seminar;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class UploadAndFileActionTest {
    final UploadValidator validator = new UploadValidator(new CoreUnitTest().app());
    static byte[] archive(String... paths) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (String path : paths) {
                zip.putNextEntry(new ZipEntry(path));
                if (!path.endsWith("/")) zip.write("<xml>Seminar</xml>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
    UploadValidator.Validated check(String name, byte[] bytes) {
        // Deliberately wrong client-supplied MIME: the server must determine it from the file.
        return validator.validate(new MockMultipartFile("file", name, "text/plain", bytes));
    }
    @Test void wordDocumentNeedsWordStructureAndUploadsWithWordMime() throws Exception {
        var word = archive("[Content_Types].xml", "word/document.xml");
        var checked = check("báo-cáo.DOCX", word);
        assertThat(checked.mime()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(checked.bytes()).isEqualTo(word);
        assertThatThrownBy(() -> check("fake.docx", archive("[Content_Types].xml", "ppt/presentation.xml")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> check("fake.docx", archive("readme.txt"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void zipKeepsOriginalBytesAndAcceptsMultipleFilesAndUppercaseExtension() throws Exception {
        var bytes = archive("Nhóm 05/", "Nhóm 05/báo-cáo.pdf", "Nhóm 05/source/Main.java");
        var checked = check("C:\\reports\\seminar.ZIP", bytes);
        assertThat(checked.name()).isEqualTo("seminar.ZIP");
        assertThat(checked.mime()).isEqualTo("application/zip");
        assertThat(checked.bytes()).isEqualTo(bytes);
    }
    @Test void emptyTruncatedAndRenamedArchivesAreRejected() throws Exception {
        assertThatThrownBy(() -> check("fake.zip", "%PDF-1.4".getBytes())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> check("empty.zip", archive())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> check("folders.zip", archive("empty/"))).isInstanceOf(IllegalArgumentException.class);
        var bytes = archive("report.txt");
        assertThatThrownBy(() -> check("cut.zip", Arrays.copyOf(bytes, bytes.length - 22)))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void damagedEntryChecksumIsRejected() throws Exception {
        byte[] data = "seminar-checksum".getBytes(StandardCharsets.UTF_8);
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            var entry = new ZipEntry("report.txt"); var crc = new CRC32(); crc.update(data);
            entry.setMethod(ZipEntry.STORED); entry.setSize(data.length); entry.setCrc(crc.getValue());
            zip.putNextEntry(entry); zip.write(data); zip.closeEntry();
        }
        var bytes = output.toByteArray();
        // The stored entry starts at 30 + filename length.
        bytes[30 + "report.txt".length()] ^= 1;
        assertThatThrownBy(() -> check("damaged.zip", bytes)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void compressedArchiveCannotBypassExpandedSizeLimit() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("large.bin"));
            var block = new byte[1048576];
            for (int i = 0; i < 101; i++) zip.write(block);
            zip.closeEntry();
        }
        assertThat(output.size()).isLessThan(1048576);
        assertThatThrownBy(() -> check("large.zip", output.toByteArray()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100 MB");
    }
    @Test void archiveEntryCountIsBounded() throws Exception {
        var paths = new String[8001];
        for (int i = 0; i < paths.length; i++) paths[i] = "file" + i + ".txt";
        assertThatThrownBy(() -> check("many.zip", archive(paths)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("8.000");
    }
    @Test void legacyWordRecognizesOleSignatureRatherThanTrustingExtension() {
        var bytes = new byte[512];
        System.arraycopy(new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1}, 0, bytes, 0, 8);
        assertThat(check("legacy.doc", bytes).mime()).isEqualTo("application/msword");
        assertThatThrownBy(() -> check("fake.doc", "not an Office document".getBytes())).isInstanceOf(IllegalArgumentException.class);
    }
    Submission submission(String name, String download) {
        var s = new CoreUnitTest().submission();
        return new Submission(s.id(), s.submittedAt(), s.studentSub(), s.studentName(), s.studentEmail(), s.groupName(),
            s.topic(), s.lecturerEmail(), s.fileId(), name, s.fileUrl(), s.fileSize(), s.phase(), false, download);
    }
    @ParameterizedTest @ValueSource(strings = {"report.pdf", "report.doc", "report.docx", "slides.ppt", "slides.pptx"})
    void documentsUseDriveViewerEvenWhenDownloadLinkIsAvailable(String name) {
        var s = submission(name, "https://drive.google.com/uc?id=file_123456&export=download");
        assertThat(s.fileActionUrl()).isEqualTo(s.safeFileUrl());
        assertThat(s.fileActionLabel()).isEqualTo("Xem tài liệu ↗");
    }
    @Test void zipUsesGoogleDownloadLinkAndFallsBackToDriveWhenUnavailable() {
        String link = "https://drive.google.com/uc?id=file_123456&export=download";
        assertThat(submission("seminar.ZIP", link).fileActionUrl()).isEqualTo(link);
        assertThat(submission("seminar.ZIP", link).fileActionLabel()).contains("Tải file ZIP");
        var fallback = submission("seminar.zip", "");
        assertThat(fallback.fileActionUrl()).isEqualTo(fallback.safeFileUrl());
        assertThat(fallback.fileActionLabel()).contains("Mở Drive để tải ZIP");
    }
    @ParameterizedTest @ValueSource(strings = {"javascript:alert(1)", "http://drive.google.com/file", "https://drive.google.com.evil.example/file",
        "https://evil.example/file", "https://user@drive.google.com/file", "https://drive.google.com:8443/file", "not a URI"})
    void untrustedDownloadLinksFallBackToDriveViewer(String link) {
        var s = submission("seminar.zip", link);
        assertThat(s.safeDownloadUrl()).isEmpty();
        assertThat(s.fileActionUrl()).isEqualTo(s.safeFileUrl());
    }
}
