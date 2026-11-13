package com.jiugeng.erp.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import static com.jiugeng.erp.customer.CustomerTypes.Address;

final class CustomerAddresses {
    private static final ObjectMapper JSON = new ObjectMapper();
    static List<Address> read(ResultSet r) throws SQLException {
        String value=r.getString("addresses");
        if(value!=null) {
            try { return JSON.readValue(value,new TypeReference<List<Address>>() {}); }
            catch(JsonProcessingException e) { throw new IllegalStateException("Invalid stored customer addresses",e); }
        }
        String address=r.getString("address");
        return address==null || address.isBlank()?List.of():List.of(new Address(r.getString("contact_person"),r.getString("phone"),address,true));
    }
    static List<Address> normalize(List<Address> entries) {
        if(entries.stream().filter(Address::defaultAddress).count()>1)
            throw new CustomerException(400,"CUSTOMER_ADDRESS_INVALID","只能设置一个默认收货地址");
        for(var entry:entries)if(!com.jiugeng.erp.ChinaPhone.contact(entry.phone()))throw new CustomerException(400,"PHONE_INVALID",com.jiugeng.erp.ChinaPhone.MESSAGE);
        var result=new ArrayList<Address>();
        boolean hasDefault=entries.stream().anyMatch(Address::defaultAddress);
        for(var a:entries) result.add(new Address(clean(a.contactPerson()),clean(a.phone()),clean(a.address()),a.defaultAddress()||(!hasDefault&&result.isEmpty())));
        return result;
    }
    static String encode(List<Address> entries) {
        try { return JSON.writeValueAsString(entries); }
        catch(JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private static String clean(String value) { return value==null?"":value.trim(); }
}
