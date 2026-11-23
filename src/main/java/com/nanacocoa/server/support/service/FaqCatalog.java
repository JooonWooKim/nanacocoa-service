package com.nanacocoa.server.support.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.support.dto.response.SupportResponses.Faq;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import static com.nanacocoa.server.common.exception.ErrorCode.FAQ_UNAVAILABLE;

@Component
public class FaqCatalog {
    private final List<Faq> entries;

    public FaqCatalog(ObjectMapper mapper, @Value("classpath:support/faq.json") Resource source) {
        entries = load(mapper, source);
    }

    private static List<Faq> load(ObjectMapper mapper, Resource source) {
        try (var stream = source.getInputStream()) {
            List<Faq> rows = mapper.readValue(stream, new TypeReference<List<Faq>>() {});
            if (rows == null) return null;
            var ids = new HashSet<String>();
            for (Faq row : rows) {
                if (row == null || !validText(row.id()) || !ids.add(row.id())
                        || !validText(row.question()) || !validText(row.answer())
                        || row.policyHref() != null && !row.policyHref().equals("policies.html?document=SHIPPING_RETURN")) {
                    return null;
                }
            }
            return List.copyOf(rows);
        } catch (IOException invalid) {
            return null;
        }
    }

    private static boolean validText(String value) { return value != null && !value.isBlank(); }

    public List<Faq> entries() {
        if (entries == null) throw new NanacocoaException(FAQ_UNAVAILABLE);
        return entries;
    }
}
