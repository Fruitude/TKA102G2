package com.fruitude.vendor.model;

import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

public interface VendorRepository extends JpaRepository<VendorVO, Integer> {

	boolean existsByTaxId(String taxId);
	
}