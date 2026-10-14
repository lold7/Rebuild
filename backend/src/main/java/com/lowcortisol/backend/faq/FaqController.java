package com.lowcortisol.backend.faq;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FaqController {
    private final FaqService faqs;

    public FaqController(FaqService faqs) { this.faqs = faqs; }

    @GetMapping("/api/faqs")
    public List<FaqResponse> list(@RequestParam(defaultValue = "") String q) { return faqs.list(q); }

    @GetMapping("/api/faqs/{id}")
    public FaqResponse get(@PathVariable String id) { return faqs.get(id); }
}
