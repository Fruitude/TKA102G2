package com.fruitude.product;
import com.fruitude.product.controller.ProductController;
import com.fruitude.product.model.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
public class ProductModalImageUploadTest {
    private static final String PAYLOAD="{\"edit\":{\"product\":{\"productId\":10},\"skus\":[],\"productStatusChanged\":false,\"activateSkus\":false},\"images\":[{\"skuId\":1,\"sortOrder\":0,\"fileIndex\":0}]}";
    @Test public void decodesMultipartPngBeforeCallingTransactionalSave() throws Exception {
        boolean[] called={false};
        var service=new ProductService(null,null,null,null,null){
            @Override public Saved saveWithImages(ImageRequest request,List<ProductImage> uploads){
                called[0]=true;assertEquals(Integer.valueOf(10),request.edit().product().productId());assertEquals(1,uploads.size());
                assertEquals("image/png",uploads.get(0).getImageType());assertEquals("tiny.png",uploads.get(0).getImageName());
                return new Saved(10,(byte)1);
            }
        };
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var mvc=MockMvcBuilders.standaloneSetup(new ProductController(service)).build();
        var response=mvc.perform(multipart("/product/saveModalWithImages").file(new MockMultipartFile("files","tiny.png","image/png",bytes.toByteArray())).param("payload",PAYLOAD)).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertTrue(called[0]);
    }
    @Test public void rejectsPretendImageWithoutCallingSave() throws Exception {
        boolean[] called={false};
        var service=new ProductService(null,null,null,null,null){@Override public Saved saveWithImages(ImageRequest request,List<ProductImage> uploads){called[0]=true;return null;}};
        var mvc=MockMvcBuilders.standaloneSetup(new ProductController(service)).build();
        var response=mvc.perform(multipart("/product/saveModalWithImages").file(new MockMultipartFile("files","bad.png","image/png",new byte[]{1,2,3})).param("payload",PAYLOAD)).andReturn().getResponse();
        assertEquals(400,response.getStatus());assertFalse(called[0]);assertTrue(response.getContentAsString().contains("只接受"));
    }
    @Test public void listsEveryRejectedFileAndOriginalIndexWithoutSavingAnyPartialChanges()throws Exception {
        boolean[] called={false};
        var service=new ProductService(null,null,null,null,null){@Override public Saved saveWithImages(ImageRequest request,List<ProductImage> uploads){called[0]=true;return null;}};
        var mvc=MockMvcBuilders.standaloneSetup(new ProductController(service)).build();
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var response=mvc.perform(multipart("/product/saveModalWithImages")
            .file(new MockMultipartFile("files","valid.png","image/png",bytes.toByteArray()))
            .file(new MockMultipartFile("files","empty.png","image/png",new byte[0]))
            .file(new MockMultipartFile("files","fake.jpg","image/jpeg",new byte[]{1,2,3}))
            .file(new MockMultipartFile("files","oversized.png","image/png",new byte[5*1024*1024+1]))
            .param("payload",PAYLOAD)).andReturn().getResponse();
        assertEquals(400,response.getStatus());assertFalse(called[0]);assertEquals("no-store",response.getHeader("Cache-Control"));
        var body=tools.jackson.databind.json.JsonMapper.builder().build().readValue(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8),Map.class);
        assertEquals("invalidImages",body.get("kind"));var rejected=(List<Map<String,Object>>)body.get("invalidImages");assertEquals(3,rejected.size());
        assertEquals(1,((Number)rejected.get(0).get("fileIndex")).intValue());assertEquals("empty.png",rejected.get(0).get("filename"));assertTrue(rejected.get(0).get("message").toString().contains("空的"));
        assertEquals(2,((Number)rejected.get(1).get("fileIndex")).intValue());assertEquals("fake.jpg",rejected.get(1).get("filename"));
        assertEquals(3,((Number)rejected.get(2).get("fileIndex")).intValue());assertEquals("oversized.png",rejected.get(2).get("filename"));
    }
}
