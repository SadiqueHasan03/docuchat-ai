package com.docuchat.web.dto;

import java.util.UUID;

public record DeleteResponse(UUID documentId, boolean deleted) {}
