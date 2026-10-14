package com.fruitude.product.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fruitude.product.model.ProductImage;
import com.fruitude.product.model.ProductImageService;
import com.fruitude.product.model.ProductSku;
import com.fruitude.product.model.ProductSkuService;

@Controller
@RequestMapping("/product/image")
public class ProductImageController {
    @Autowired private ProductImageService productImageService;
    @Autowired private ProductSkuService productSkuService;
    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> getImage(@PathVariable Integer imageId,
            @RequestParam(required=false) String v, org.springframework.web.context.request.WebRequest request) throws IOException {
        return imageResponse(imageId,false,v,request);
    }
    @GetMapping("/{imageId}/thumbnail")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable Integer imageId,
            @RequestParam(required=false) String v, org.springframework.web.context.request.WebRequest request) throws IOException {
        return imageResponse(imageId,true,v,request);
    }
    private ResponseEntity<byte[]> imageResponse(Integer id,boolean thumbnail,String version,
            org.springframework.web.context.request.WebRequest request) throws IOException {
        var image=productImageService.getCachedImage(id,thumbnail);
        if(image==null) return ResponseEntity.notFound().cacheControl(org.springframework.http.CacheControl.noStore()).build();
        var cache=productImageService.imageVersion(id).equals(version)
            ? org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(30)).cachePrivate()
            : org.springframework.http.CacheControl.noCache().cachePrivate();
        if(request.checkNotModified(image.etag())) return ResponseEntity.status(304).eTag(image.etag()).cacheControl(cache).build();
        return ResponseEntity.ok().eTag(image.etag()).cacheControl(cache)
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename("image-"+id).build().toString())
            .contentType(MediaType.parseMediaType(image.type())).body(image.data());
    }
    @GetMapping("/list")
    public String list(@RequestParam(required = false) Integer skuId, Model model) {
        model.addAttribute("images", productImageService.getAll().stream().filter(i -> skuId == null || (i.getProductSku() != null && skuId.equals(i.getProductSku().getSkuId()))).toList());
        model.addAttribute("skuId", skuId);
        model.addAttribute("skus", productSkuService.getAll());
        return "admin/productmanagement/productimage/list";
    }
    @GetMapping("/add")
    public String add(@RequestParam(required = false) Integer skuId, Model model) {
        model.addAttribute("image", new ProductImage());
        model.addAttribute("skuId", skuId);
        model.addAttribute("skus", productSkuService.getAll());
        return "admin/productmanagement/productimage/add";
    }
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Integer id, Model model, RedirectAttributes redirect) {
        ProductImage image = productImageService.getOneProductImage(id);
        if (image == null) { redirect.addFlashAttribute("errorMessage", "圖片不存在"); return "redirect:/product/image/list"; }
        model.addAttribute("image", image);
        model.addAttribute("skuId", image.getProductSku().getSkuId());
        model.addAttribute("skus", productSkuService.getAll());
        return "admin/productmanagement/productimage/edit";
    }
    @PostMapping("/insert")
    public String insert(@RequestParam Integer skuId, @RequestParam MultipartFile file,
        @RequestParam(defaultValue = "0") Integer sortOrder, Model model, RedirectAttributes redirect) {
        ProductSku sku = productSkuService.getOneProductSku(skuId);
        ProductImage image = new ProductImage();
        image.setSortOrder(sortOrder);
        model.addAttribute("image", image);
        model.addAttribute("skuId", skuId);
        model.addAttribute("skus", productSkuService.getAll());
        try {
            if (sku == null) throw new IllegalArgumentException("規格不存在");
            if (sortOrder < 0) throw new IllegalArgumentException("排序不可小於 0");
            com.fruitude.product.model.ProductImageService.ImageUploadSupport.apply(image, file);
            image.setProductSku(sku);
            productImageService.addProductImage(image);
        } catch (IOException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e instanceof IOException ? "圖片讀取失敗" : e.getMessage()); return "admin/productmanagement/productimage/add";
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            model.addAttribute("errorMessage", "所屬規格已變更，請重新選擇"); return "admin/productmanagement/productimage/add";
        }
        redirect.addFlashAttribute("successMessage", "圖片新增成功");
        return "redirect:/product/image/list?skuId=" + skuId;
    }
    @PostMapping("/update")
    public String update(@RequestParam Integer imageId, @RequestParam(required = false) MultipartFile file,
        @RequestParam Integer sortOrder, Model model, RedirectAttributes redirect) {
        ProductImage original = productImageService.getOneProductImage(imageId);
        if (original == null) { redirect.addFlashAttribute("errorMessage", "圖片不存在"); return "redirect:/product/image/list"; }
        // Prepare replacement separately so a rejected upload cannot alter the persisted image.
        ProductImage replacement = new ProductImage();
        try {
            if (sortOrder < 0) throw new IllegalArgumentException("排序不可小於 0");
            if (file != null && !file.isEmpty()) com.fruitude.product.model.ProductImageService.ImageUploadSupport.apply(replacement, file);
            productImageService.replaceImage(imageId, sortOrder, replacement);
        } catch (IOException | IllegalArgumentException e) {
            model.addAttribute("image", original);
            model.addAttribute("sortOrder", sortOrder);
            model.addAttribute("skuId", original.getProductSku().getSkuId());
            model.addAttribute("skus", productSkuService.getAll());
            model.addAttribute("errorMessage", e instanceof IOException ? "圖片讀取失敗" : e.getMessage());
            return "admin/productmanagement/productimage/edit";
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirect.addFlashAttribute("errorMessage", "圖片或規格已變更，請重新操作"); return "redirect:/product/image/list";
        }
        redirect.addFlashAttribute("successMessage", "圖片修改成功");
        return "redirect:/product/image/list?skuId=" + original.getProductSku().getSkuId();
    }
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Integer id, RedirectAttributes redirect) {
        ProductImage image = productImageService.getOneProductImage(id);
        if (image == null) { redirect.addFlashAttribute("errorMessage", "圖片不存在"); return "redirect:/product/image/list"; }
        Integer skuId = image.getProductSku().getSkuId();
        try { productImageService.deleteProductImage(id); }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirect.addFlashAttribute("errorMessage", "此圖片仍被其他資料引用，不能刪除");
            return "redirect:/product/image/list?skuId=" + skuId;
        }
        redirect.addFlashAttribute("successMessage", "圖片刪除成功");
        return "redirect:/product/image/list?skuId=" + skuId;
    }
}
