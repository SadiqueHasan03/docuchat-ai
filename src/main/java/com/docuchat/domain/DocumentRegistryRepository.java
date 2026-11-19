package com.docuchat.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRegistryRepository extends JpaRepository<DocumentRegistry, UUID> {

    List<DocumentRegistry> findByUserIdOrderByUploadedAtDesc(String userId);

    Optional<DocumentRegistry> findByDocumentIdAndUserId(UUID documentId, String userId);

    List<DocumentRegistry> findByUserIdAndSha256Hash(String userId, String sha256Hash);

    Optional<DocumentRegistry> findFirstByUserIdAndSha256HashAndStatus(String userId, String sha256Hash,
                                                                       DocumentStatus status);
}
