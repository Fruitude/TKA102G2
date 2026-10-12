package com.fruitude.product.controller;
import com.fruitude.product.model.FrontProductReviewService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@RestController
public class FrontProductReviewController {
    private final FrontProductReviewService reviews;
    public FrontProductReviewController(FrontProductReviewService reviews) { this.reviews=reviews; }
    @GetMapping("/front/api/product-reviews")
    public ResponseEntity<?> read(@RequestParam int productId,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="latest") String sort) {
        if(productId<=0 || page<0 || page>100000)return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.read(productId,page,20,keyword,sort));
        } catch(IllegalArgumentException ex) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(java.util.Map.of("message",ex.getMessage()));
        }
    }
}
