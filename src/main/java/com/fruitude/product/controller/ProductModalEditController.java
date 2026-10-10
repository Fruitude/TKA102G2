package com.fruitude.product.controller;
import com.fruitude.product.model.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
@RequestMapping("/product")
public class ProductModalEditController {
    private final ProductModalEditService service;
    public ProductModalEditController(ProductModalEditService service) { this.service=service; }
    @GetMapping("/editData") public ProductModalEditService.EditData load(@RequestParam Integer productId) { return service.load(productId); }
    @PostMapping("/saveModal") public ProductModalEditService.Saved save(@RequestBody ProductModalEditService.SaveRequest request) { return service.save(request); }
    @ExceptionHandler(ProductStatusAccessException.class) public ResponseEntity<?> denied(ProductStatusAccessException e) { return error(403,"denied",e.getMessage()); }
    @ExceptionHandler(ProductStatusConfirmationException.class) public ResponseEntity<?> confirm(ProductStatusConfirmationException e) { return error(409,"confirm",e.getMessage()); }
    @ExceptionHandler(ProductModalEditService.Conflict.class) public ResponseEntity<?> conflict(ProductModalEditService.Conflict e) { return error(409,"conflict",e.getMessage()); }
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<?> missing(NoSuchElementException e) { return error(404,"missing",e.getMessage()); }
    @ExceptionHandler(SkuRestockConfirmationException.class) public ResponseEntity<?> restock(SkuRestockConfirmationException e) { return error(409,"restock",e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e) { return error(400,"invalid",e.getMessage()); }
    private ResponseEntity<?> error(int status,String kind,String message) { return ResponseEntity.status(status).body(Map.of("kind",kind,"message",message)); }
}
