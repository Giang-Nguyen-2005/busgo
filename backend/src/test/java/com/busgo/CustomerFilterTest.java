package com.busgo;
import static org.assertj.core.api.Assertions.*;
import com.busgo.customer.*;
import com.busgo.customer.CustomerDtos.*;
import org.junit.jupiter.api.Test;
class CustomerFilterTest {
    @Test void defaultsAndLiteralSearch() {
        var f=new CustomerFilter(" A%_! ",null,null,null,null);f.validate();
        assertThat(f.pageSize()).isEqualTo(20);assertThat(f.ordering()).isEqualTo(Sort.LATEST);
        assertThat(f.search()).isEqualTo("%a!%!_!!%");
        assertThat(new CustomerFilter("  ",null,null,null,null).search()).isNull();
    }
    @Test void boundedPaginationAndTypedKeys() {
        for(var f:java.util.List.of(new CustomerFilter(null,null,null,-1,20),new CustomerFilter(null,null,null,0,101),new CustomerFilter("x".repeat(151),null,null,0,20)))
            assertThatThrownBy(f::validate).hasMessageContaining("Invalid");
        for(var key:java.util.List.of("CONTACT:0","ACCOUNT:9223372036854775808","PHONE:1","CONTACT:0901234567","ACCOUNT:1 OR 1=1"))
            assertThatThrownBy(()->CustomerFilter.validateKey(key)).hasMessageContaining("Invalid");
        CustomerFilter.validateKey("CONTACT:1");CustomerFilter.validateKey("ACCOUNT:2");
    }
}
