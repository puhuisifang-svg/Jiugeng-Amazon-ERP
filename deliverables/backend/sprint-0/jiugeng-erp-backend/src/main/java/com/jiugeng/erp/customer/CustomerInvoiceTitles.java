package com.jiugeng.erp.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import static com.jiugeng.erp.customer.CustomerTypes.InvoiceTitle;

@Service
class CustomerInvoiceTitles {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcClient jdbc;
    CustomerInvoiceTitles(JdbcClient jdbc) { this.jdbc=jdbc; }
    List<InvoiceTitle> get(String customerId) {
        return jdbc.sql("SELECT * FROM customer WHERE id=:id").param("id",customerId)
            .query((r,n)->read(r)).single();
    }
    static List<InvoiceTitle> read(ResultSet r) throws SQLException {
        String json=r.getString("invoice_titles");
        if(json!=null) {
            try { return JSON.readValue(json,new TypeReference<List<InvoiceTitle>>() {}); }
            catch(JsonProcessingException e) { throw new IllegalStateException("Invalid stored invoice titles",e); }
        }
        // Preserve pre-migration billing data until explicitly edited.
        var legacy=new InvoiceTitle(r.getString("invoice_title"),r.getString("tax_number"),
            r.getString("registered_address"),r.getString("registered_phone"),r.getString("bank_name"),r.getString("bank_account"));
        return legacy.invoiceTitle().isBlank() && legacy.taxNumber().isBlank() && legacy.registeredAddress().isBlank()
            && legacy.registeredPhone().isBlank() && legacy.bankName().isBlank() && legacy.bankAccount().isBlank() ? List.of() : List.of(legacy);
    }
    static String encode(List<InvoiceTitle> entries) {
        var normalized=entries.stream().map(t->new InvoiceTitle(clean(t.invoiceTitle()),clean(t.taxNumber()),
            clean(t.registeredAddress()),clean(t.registeredPhone()),clean(t.bankName()),clean(t.bankAccount()))).toList();
        var keys=new HashSet<List<String>>();
        for(var t:normalized) {
            if(t.invoiceTitle().isBlank() || !keys.add(List.of(t.invoiceTitle(),t.taxNumber())))
                throw new CustomerException(400,"CUSTOMER_INVOICE_TITLE_INVALID","发票抬头不能为空，同一抬头和税号不能重复");
        }
        try { return JSON.writeValueAsString(normalized); }
        catch(JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private static String clean(String value) {
        String result=value==null?"":value.trim();
        if(result.chars().anyMatch(c->c<32 && c!='\n' && c!='\t'))
            throw new CustomerException(400,"CUSTOMER_INVOICE_TITLE_INVALID","开票信息包含无效字符");
        return result;
    }
}
