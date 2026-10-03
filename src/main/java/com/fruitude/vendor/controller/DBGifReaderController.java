package com.fruitude.vendor.controller;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fruitude.vendor.model.VendorService;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/vendor")
public class DBGifReaderController {
	
	@Autowired
	VendorService vendorSvc;
	
	/*
	 * This method will serve as listOneEmp.html , listAllEmp.html handler.
	 */
	@GetMapping("DBGifReader")
	public void dBGifReader(@RequestParam("vendorId") String vendorId, HttpServletRequest req, HttpServletResponse res)
			                                                                                          throws IOException {
		res.setContentType("image/gif");
		ServletOutputStream out = res.getOutputStream();

		try {
			out.write(vendorSvc.getOneVendor(Integer.valueOf(vendorId)).getLogo());
		} catch (Exception e) {
			System.out.println(e);
			// 取不到圖片時，顯示預設圖片
			res.setContentType("image/jpeg");
			try (InputStream in = new ClassPathResource("static/img/no_Image.jpg").getInputStream()) {
				in.transferTo(out);
			}
		}
	}
}