package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.user.entity.RoleCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class OperatorMediaIT extends M16ATestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    Fixture a,b;
    String token,foreign,base,publicPath;
    byte[] png;
    @BeforeEach void setup() throws Exception {
        a=fixture(); b=fixture();
        token=bearer(actor(a,RoleCode.OPERATOR_ADMIN)); foreign=bearer(actor(b,RoleCode.OPERATOR_ADMIN));
        base="/api/v1/operator/bus-types/"+a.busType().getId()+"/media";
        publicPath="/api/v1/public/operators/"+a.operator().getId()+"/media";
        var stream=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",stream);
        png=stream.toByteArray();
    }
    MockMultipartFile image() { return new MockMultipartFile("files","bus.png","image/png",png); }
    @Test void ownershipAndPublicProjection() throws Exception {
        // Both operators run this catalogue type. Only the gallery owner may mutate its IDs.
        jdbc.update("UPDATE buses SET bus_type_id=? WHERE id=?",a.busType().getId(),b.bus().getId());
        mvc.perform(multipart(base).file(image()).param("category","SEAT").param("operatorId",b.operator().getId().toString())
            .header("Authorization",token)).andExpect(status().isOk());
        var body=mvc.perform(get(base).header("Authorization",token)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String id=mapper.readTree(body).path("data").get(0).path("id").asText();
        mvc.perform(get(publicPath)).andExpect(status().isOk()).andExpect(jsonPath("data.photos[0].id").value(id))
            .andExpect(jsonPath("data.photos[0].content").doesNotExist());
        mvc.perform(get(publicPath+"/"+id)).andExpect(content().bytes(png)).andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(delete(base+"/"+id).header("Authorization",foreign)).andExpect(status().isNotFound());
        mvc.perform(patch(base+"/"+id).header("Authorization",foreign).contentType("application/json")
            .content("{\"category\":\"SEAT\",\"alt\":\"foreign\"}")).andExpect(status().isNotFound());
        var customer=bearer(customer("media").user());
        mvc.perform(multipart(base).file(image()).param("category","SEAT").header("Authorization",customer)).andExpect(status().isForbidden());
        mvc.perform(delete(base+"/"+id).header("Authorization",customer)).andExpect(status().isForbidden());
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(multipart(base).file(image()).param("category","SEAT")).andExpect(status().isUnauthorized());
        mvc.perform(delete(base+"/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get(publicPath)).andExpect(jsonPath("data.photos[0].alt").value(""));
        mvc.perform(get(publicPath+"/"+UUID.randomUUID())).andExpect(status().isNotFound());
        jdbc.update("UPDATE buses SET status='MAINTENANCE' WHERE id=?",a.bus().getId());
        mvc.perform(get(publicPath+"/"+id)).andExpect(status().isNotFound());
        mvc.perform(get(base+"/"+id).header("Authorization",token)).andExpect(content().bytes(png));
    }
    @Test void validateFileAndAssociation() throws Exception {
        for(var file:new MockMultipartFile[]{new MockMultipartFile("files","x.svg","image/svg+xml","<svg/>".getBytes()),
            new MockMultipartFile("files","x.png","image/png","<script>".getBytes()),
            new MockMultipartFile("files","x.jpg","image/jpeg",png),new MockMultipartFile("files","x.exe","image/png",png),
            new MockMultipartFile("files","x.png","image/png",new byte[5*1024*1024+1])}) {
            mvc.perform(multipart(base).file(file).param("category","SEAT").header("Authorization",token)).andExpect(status().isBadRequest());
        }
        mvc.perform(multipart(base).file(image()).param("category","SEAT").header("Authorization",foreign)).andExpect(status().isBadRequest());
        mvc.perform(multipart(base).param("category","SEAT").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get(base).header("Authorization",token)).andExpect(jsonPath("data.length()").value(0));
    }
    @Test void reorderAndReplaceFeatured() throws Exception {
        mvc.perform(multipart(base).file(image()).file(image()).param("category","INTERIOR").header("Authorization",token)).andExpect(status().isOk());
        var files=mapper.readTree(mvc.perform(get(base).header("Authorization",token)).andReturn().getResponse().getContentAsString()).path("data");
        String first=files.get(0).path("id").asText(), second=files.get(1).path("id").asText();
        mvc.perform(put(base+"/order").header("Authorization",token).contentType("application/json")
            .content("{\"ids\":[\""+second+"\",\""+first+"\"]}")).andExpect(status().isOk());
        mvc.perform(get(publicPath)).andExpect(jsonPath("data.photos[0].id").value(second));
        mvc.perform(patch(base+"/"+second).header("Authorization",token).contentType("application/json")
            .content("{\"category\":\"SEAT\",\"alt\":\"New seat\",\"featured\":true}")).andExpect(status().isOk());
        mvc.perform(delete(base+"/"+second).header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get(publicPath)).andExpect(jsonPath("data.photos[0].featured").value(true)).andExpect(jsonPath("data.photos.length()").value(1));
        mvc.perform(get(publicPath+"/"+second)).andExpect(status().isNotFound());
    }
    @Test void logoReplacementRetiresPreviousBytesAndDescriptionKeepsLogo() throws Exception {
        var logo=new MockMultipartFile("file","logo.png","image/png",png);
        String url="/api/v1/operator/marketplace-profile/logo";
        String previous=null;
        for(int i=0;i<2;i++) {
            mvc.perform(multipart(url).file(logo).header("Authorization",token)).andExpect(status().isOk());
            String current=mapper.readTree(mvc.perform(get(publicPath)).andReturn().getResponse()
                .getContentAsString()).path("data").path("logoUrl").asText();
            mvc.perform(get(current)).andExpect(content().bytes(png));
            if(previous!=null) mvc.perform(get(previous)).andExpect(status().isNotFound());
            previous=current;
        }
        mvc.perform(patch("/api/v1/operator/marketplace-profile").header("Authorization",token)
            .contentType("application/json").content("{\"description\":\"Test profile\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("data.logoUrl").value(previous));
        mvc.perform(delete(url).header("Authorization",foreign)).andExpect(status().isOk());
        mvc.perform(get(previous)).andExpect(content().bytes(png));
        mvc.perform(delete(url).header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get(previous)).andExpect(status().isNotFound());
    }
    @Test void capAndInvalidReorderKeepGalleryIntact() throws Exception {
        var batch=multipart(base);
        for(int i=0;i<12;i++) batch.file(image());
        mvc.perform(batch.param("category","SEAT").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(multipart(base).file(image()).param("category","SEAT").header("Authorization",token))
            .andExpect(status().isBadRequest());
        mvc.perform(put(base+"/order").header("Authorization",token).contentType("application/json")
            .content("{\"ids\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(get(base).header("Authorization",token)).andExpect(jsonPath("data.length()").value(12))
            .andExpect(jsonPath("data[0].featured").value(true));
    }
}
