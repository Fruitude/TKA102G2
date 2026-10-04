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
import com.fruitude.vendor.model.VendorVO;

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
		byte[] logo = null;
		try {
			VendorVO vendorVO = vendorSvc.getOneVendor(Integer.valueOf(vendorId));
			if (vendorVO != null) {
				logo = vendorVO.getLogo();
			}
		} catch (NumberFormatException e) {
			// 編號不是數字時，一樣顯示預設圖片
		}

		ServletOutputStream out = res.getOutputStream();

		if (logo != null && logo.length > 0) {
			res.setContentType("image/gif");
			out.write(logo);
		} else {
			// 沒有品牌標誌時，顯示預設圖片
			res.setContentType("image/jpeg");
			try (InputStream in = new ClassPathResource("static/img/no_Image.jpg").getInputStream()) {
				in.transferTo(out);
			}
		}
	}
}