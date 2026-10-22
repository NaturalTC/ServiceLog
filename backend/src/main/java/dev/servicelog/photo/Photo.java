package dev.servicelog.photo;

import dev.servicelog.servicerecord.ServiceRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "service_record_photo")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_record_id")
    private ServiceRecord serviceRecord;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "thumb_key", nullable = false)
    private String thumbKey;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Photo() {
        // for JPA
    }

    public Photo(ServiceRecord serviceRecord, String storageKey, String thumbKey, int width, int height,
                 int sizeBytes) {
        this.serviceRecord = serviceRecord;
        this.storageKey = storageKey;
        this.thumbKey = thumbKey;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() { return id; }
    public ServiceRecord getServiceRecord() { return serviceRecord; }
    public String getStorageKey() { return storageKey; }
    public String getThumbKey() { return thumbKey; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
