package com.lowcortisol.backend.faq;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FaqService {
    private final FaqRepository faqs;

    public FaqService(FaqRepository faqs) { this.faqs = faqs; }

    public List<FaqResponse> list(String query) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.length() > 255) throw FaqException.invalid("q must contain at most 255 characters");
        return faqs.list(keyword);
    }

    public FaqResponse get(String id) {
        long faqId;
        try {
            if (id == null || !id.matches("\\d+")) throw new NumberFormatException();
            faqId = Long.parseLong(id);
            if (faqId < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) { throw FaqException.invalid("FAQ ID must be a positive 64-bit integer"); }
        return faqs.get(faqId).orElseThrow(FaqException::missing);
    }
}
