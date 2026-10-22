package dev.servicelog.photo;

import dev.servicelog.exception.InvalidRequestException;
import dev.servicelog.exception.NotFoundException;
import dev.servicelog.photo.PhotoDtos.PhotoResponse;
import dev.servicelog.servicerecord.ServiceRecord;
import dev.servicelog.servicerecord.ServiceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    private final PhotoRepository photos;
    private final ServiceRecordRepository records;
    private final PhotoStorage storage;
    private final PhotoLinks links;
    private final int maxPerRecord;

    public PhotoService(PhotoRepository photos, ServiceRecordRepository records, PhotoStorage storage,
                        PhotoLinks links, @Value("${servicelog.photos.max-per-record}") int maxPerRecord) {
        this.photos = photos;
        this.records = records;
        this.storage = storage;
        this.links = links;
        this.maxPerRecord = maxPerRecord;
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> list(long recordId) {
        if (!records.existsById(recordId)) {
            throw new NotFoundException("Service record not found");
        }
        return photos.findByServiceRecordIdOrderByIdAsc(recordId).stream().map(this::toResponse).toList();
    }

    // All photos are checked and processed before any is saved, so one bad file adds nothing
    @Transactional
    public List<PhotoResponse> add(long recordId, List<MultipartFile> files) {
        ServiceRecord record = records.findById(recordId)
                .orElseThrow(() -> new NotFoundException("Service record not found"));
        if (files == null || files.isEmpty()) {
            throw new InvalidRequestException("Choose at least one photo");
        }
        if (photos.countByServiceRecordId(recordId) + files.size() > maxPerRecord) {
            throw new InvalidRequestException("A service record can have up to " + maxPerRecord + " photos");
        }

        List<ImageProcessor.Processed> processed = new ArrayList<>();
        for (MultipartFile file : files) {
            processed.add(ImageProcessor.process(bytes(file)));
        }

        List<String> written = new ArrayList<>();
        try {
            List<PhotoResponse> added = new ArrayList<>();
            for (ImageProcessor.Processed p : processed) {
                String id = UUID.randomUUID().toString();
                String key = id + ".jpg";
                String thumbKey = id + "-thumb.jpg";
                storage.save(key, p.full());
                written.add(key);
                storage.save(thumbKey, p.thumb());
                written.add(thumbKey);
                Photo saved = photos.save(new Photo(record, key, thumbKey, p.width(), p.height(), p.full().length));
                added.add(toResponse(saved));
            }
            return added;
        } catch (RuntimeException e) {
            // The database changes roll back; remove the files too, so none are left orphaned
            written.forEach(this::deleteQuietly);
            throw e;
        }
    }

    @Transactional
    public void delete(long photoId) {
        Photo photo = photos.findById(photoId).orElseThrow(() -> new NotFoundException("Photo not found"));
        photos.delete(photo);
        photos.flush();
        deleteQuietly(photo.getStorageKey());
        deleteQuietly(photo.getThumbKey());
    }

    // The image bytes for a signed link; empty when the photo is gone
    @Transactional(readOnly = true)
    public byte[] image(long photoId, boolean thumb) {
        Photo photo = photos.findById(photoId).orElseThrow(() -> new NotFoundException("Photo not found"));
        return storage.load(thumb ? photo.getThumbKey() : photo.getStorageKey());
    }

    private PhotoResponse toResponse(Photo p) {
        return new PhotoResponse(p.getId(), links.url(p.getId(), "full"), links.url(p.getId(), "thumb"),
                p.getWidth(), p.getHeight(), p.getCreatedAt());
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("That photo couldn't be uploaded. Try again.");
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Couldn't delete photo file {}: {}", key, e.getMessage());
        }
    }
}
