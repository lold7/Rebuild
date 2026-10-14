package com.lowcortisol.backend.faq;

public record FaqResponse(long faqId, String question, String answer, int sortOrder) {}
