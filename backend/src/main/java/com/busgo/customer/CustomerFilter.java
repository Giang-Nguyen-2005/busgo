package com.busgo.customer;

import com.busgo.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import com.busgo.customer.CustomerDtos.*;

public record CustomerFilter(String q, Type customerType, Sort sort, Integer page, Integer size) {
    public int pageNumber() { return page == null ? 0 : page; }
    public int pageSize() { return size == null ? 20 : size; }
    public Sort ordering() { return sort == null ? Sort.LATEST : sort; }
    public String search() {
        if (q == null || q.isBlank()) return null;
        return "%" + q.strip().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    public void validate() {
        if (pageNumber() < 0 || pageNumber() > 100000 || pageSize() < 1 || pageSize() > 100
                || (q != null && q.length() > 150)) throw invalid();
    }
    public static BusinessException invalid() {
        return new BusinessException("INVALID_CUSTOMER_FILTER", "Invalid customer key or filters.", HttpStatus.BAD_REQUEST, null);
    }
    public static void validateKey(String key) {
        if (key == null || !key.matches("(ACCOUNT|CONTACT):[1-9][0-9]{0,18}")) throw invalid();
        try { Long.parseLong(key.substring(key.indexOf(':') + 1)); }
        catch (NumberFormatException e) { throw invalid(); }
    }
}
