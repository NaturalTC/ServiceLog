package dev.servicelog.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByServiceRecordIdOrderByIdAsc(Long serviceRecordId);

    long countByServiceRecordId(Long serviceRecordId);
}
