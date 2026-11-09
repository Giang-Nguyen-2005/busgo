package com.busgo.demo;

import com.busgo.demo.m31.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class M31CatalogueTest {
    @Test void productionAndLegacyDemoProfilesCannotInvokeApplyEvenDirectly() {
        for(String[] profiles:new String[][]{{"prod"},{"dev","production"},{"dev","demo"}}) {
            var env=new MockEnvironment(); env.setActiveProfiles(profiles);
            var jdbc=mock(JdbcTemplate.class);
            var service=new M31CatalogueService(null,null,null,null,jdbc,env,"busgo_m312_test");
            assertThatThrownBy(()->service.apply(M31Catalogue.Batch.FULL)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(jdbc);
        }
    }
    @Test void applicationSchemaCannotBeAdoptedBySettingVerifiedSchema() {
        var env=new MockEnvironment(); env.setActiveProfiles("dev");
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT DATABASE()",String.class)).thenReturn("busgo_db");
        var service=new M31CatalogueService(null,null,null,null,jdbc,env,"busgo_db");
        assertThatThrownBy(()->service.apply(M31Catalogue.Batch.PILOT))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("target denied");
        verify(jdbc,never()).queryForObject("SELECT id FROM roles WHERE code='CUSTOMER' FOR UPDATE",Long.class);
    }
}
