package com.contentaggregator.core.content;

import java.util.UUID;

public record SearchResultItem(
    UUID contentId, String title, String url, String cleanContent, double rrfScore) {}
