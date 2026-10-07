package com.fruitude.podetail.model;

import com.fruitude.po.model.PoVO;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "purchaseorder_details",
    uniqueConstraints = @UniqueConstraint(columnNames = {"po_id", "sku_id"})
)
public class PoDetailVO implements java.io.Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id",updatable=false)
    private Integer poDetailId;                          // 採購明細編號

    @NotNull(message = "採購單系統編號，請勿空白")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id", nullable = false)
    private PoVO poId;                         // 採購單系統編號 (FK)

    @NotNull(message = "商品規格編號，請勿空白")
    @Column(name = "sku_id", nullable = false)
    private Integer skuId;                       // 商品規格編號 (FK)

    @NotNull(message = "採購數量，請勿空白")
    @Min(value = 1, message = "採購數量必須大於0")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;                    // 採購數量

    @NotNull(message = "進貨單價，請勿空白")
    @Min(value = 0, message = "進貨單價不可為負數")
    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;                   // 進貨單價

    @NotNull(message = "小計，請勿空白")
    @Min(value = 0, message = "小計不可為負數")
    @Column(name = "subtotal", nullable = false)
    private Integer subtotal;                    // 小計

    @NotNull(message = "到貨數量，請勿空白")
    @Min(value = 0, message = "到貨數量不可為負數")
    @Column(name = "arrived_pcs", nullable = false)
    private Integer arrivedPcs = 0;              // 到貨數量

    @NotNull(message = "不良品數量，請勿空白")
    @Min(value = 0, message = "不良品數量不可為負數")
    @Column(name = "defect_pcs", nullable = false)
    private Integer defectPcs = 0;               // 不良品數量

    @NotNull(message = "實際入庫數量，請勿空白")
    @Min(value = 0, message = "實際入庫數量不可為負數")
    @Column(name = "inbound_pcs", nullable = false)
    private Integer inboundPcs = 0;              // 實際入庫數量

    @NotNull(message = "驗收小計，請勿空白")
    @Min(value = 0, message = "驗收小計不可為負數")
    @Column(name = "inbound_subtotal", nullable = false)
    private Integer inboundSubtotal = 0;         // 驗收小計

    // 對應資料庫的 CHECK (arrived_pcs <= quantity)
    @AssertTrue(message = "到貨數量不可大於採購數量")
    public boolean isArrivedPcsValid() {
        if (arrivedPcs == null || quantity == null) {
            return true;  // 空值交給 @NotNull 處理
        }
        return arrivedPcs <= quantity;
    }

	public Integer getPoDetailId() {
		return poDetailId;
	}

	public PoVO getPoId() {
		return poId;
	}

	public Integer getSkuId() {
		return skuId;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public Integer getUnitPrice() {
		return unitPrice;
	}

	public Integer getSubtotal() {
		return subtotal;
	}

	public Integer getArrivedPcs() {
		return arrivedPcs;
	}

	public Integer getDefectPcs() {
		return defectPcs;
	}

	public Integer getInboundPcs() {
		return inboundPcs;
	}

	public Integer getInboundSubtotal() {
		return inboundSubtotal;
	}

	public void setPoDetailId(Integer poDetailId) {
		this.poDetailId = poDetailId;
	}

	public void setPoId(PoVO poId) {
		this.poId = poId;
	}

	public void setSkuId(Integer skuId) {
		this.skuId = skuId;
	}

	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}

	public void setUnitPrice(Integer unitPrice) {
		this.unitPrice = unitPrice;
	}

	public void setSubtotal(Integer subtotal) {
		this.subtotal = subtotal;
	}

	public void setArrivedPcs(Integer arrivedPcs) {
		this.arrivedPcs = arrivedPcs;
	}

	public void setDefectPcs(Integer defectPcs) {
		this.defectPcs = defectPcs;
	}

	public void setInboundPcs(Integer inboundPcs) {
		this.inboundPcs = inboundPcs;
	}

	public void setInboundSubtotal(Integer inboundSubtotal) {
		this.inboundSubtotal = inboundSubtotal;
	}

	


}
