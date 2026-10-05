package com.fruitude.product.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(assignableTypes = {ProductController.class, ProductSkuController.class, ProductCategoryController.class, ProductImageController.class})
public class ProductRequestAdvice {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String oversizedUpload(RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", "單張圖片不可超過 5 MB，整次上傳不可超過 30 MB，請重新選擇檔案");
        return "redirect:/product/image/list";
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String invalidParameter(RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", "編號或數值格式不正確，請重新操作");
        return "redirect:/";
    }
}
