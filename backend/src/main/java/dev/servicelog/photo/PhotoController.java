package dev.servicelog.photo;

import dev.servicelog.photo.PhotoDtos.PhotoResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

// Listing, adding and deleting need login (see SecurityConfig). The image files are served
// by signed links instead, because an <img> tag can't send the login token.
@RestController
public class PhotoController {

    private final PhotoService photoService;
    private final PhotoLinks links;

    public PhotoController(PhotoService photoService, PhotoLinks links) {
        this.photoService = photoService;
        this.links = links;
    }

    @GetMapping("/api/service-records/{recordId}/photos")
    public List<PhotoResponse> list(@PathVariable long recordId) {
        return photoService.list(recordId);
    }

    @PostMapping(value = "/api/service-records/{recordId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<PhotoResponse> add(@PathVariable long recordId, @RequestPart("files") List<MultipartFile> files) {
        return photoService.add(recordId, files);
    }

    @DeleteMapping("/api/photos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        photoService.delete(id);
    }

    // No login: the signature is the permission. Anything invalid gets the same 404.
    @GetMapping("/api/photos/{id}/{size}")
    public ResponseEntity<byte[]> image(@PathVariable long id, @PathVariable String size,
                                        @RequestParam(required = false) Long exp,
                                        @RequestParam(required = false) String sig) {
        boolean known = size.equals("full") || size.equals("thumb");
        if (!known || exp == null || !links.isValid(id, size, exp, sig)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(photoService.image(id, size.equals("thumb")));
    }
}
