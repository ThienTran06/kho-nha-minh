package com.khonhaminh.product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="products")
public class Product {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(length=80,unique=true) private String sku;
 @Column(nullable=false,length=180) private String name;
 @Column(nullable=false,length=60) private String unit;
 @Column(nullable=false,length=80) private String category="Khác";
 @Column(nullable=false,precision=19,scale=3) private BigDecimal quantity=BigDecimal.ZERO;
 @Column(name="min_stock",nullable=false,precision=19,scale=3) private BigDecimal minStock=BigDecimal.ZERO;
 @Column(name="unit_price",nullable=false,precision=19,scale=2) private BigDecimal unitPrice=BigDecimal.ZERO;
 @Column(name="target_stock",nullable=false,precision=19,scale=3) private BigDecimal targetStock=BigDecimal.ZERO;
 @Column(name="package_unit",length=60) private String packageUnit;
 @Column(name="category_locked",nullable=false) private boolean categoryLocked=false;
 @Column(name="units_per_package",nullable=false,precision=19,scale=3) private BigDecimal unitsPerPackage=BigDecimal.ZERO;
 @Column(length=500) private String note;
 @Version private Long version;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @PrePersist void pre(){var n=LocalDateTime.now();createdAt=n;updatedAt=n;if(quantity==null)quantity=BigDecimal.ZERO;if(category==null||category.isBlank())category="Khác";if(minStock==null)minStock=BigDecimal.ZERO;if(unitPrice==null)unitPrice=BigDecimal.ZERO;if(targetStock==null)targetStock=BigDecimal.ZERO;if(unitsPerPackage==null)unitsPerPackage=BigDecimal.ZERO;}
 @PreUpdate void upd(){updatedAt=LocalDateTime.now();}
 public Long getId(){return id;} public String getSku(){return sku;} public void setSku(String v){sku=v;} public String getName(){return name;} public void setName(String v){name=v;} public String getUnit(){return unit;} public void setUnit(String v){unit=v;} public String getCategory(){return category;} public void setCategory(String v){category=v;} public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;} public BigDecimal getMinStock(){return minStock;} public void setMinStock(BigDecimal v){minStock=v;} public BigDecimal getUnitPrice(){return unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;} public BigDecimal getTargetStock(){return targetStock;} public void setTargetStock(BigDecimal v){targetStock=v;} public String getPackageUnit(){return packageUnit;} public void setPackageUnit(String v){packageUnit=v;} public boolean isCategoryLocked(){return categoryLocked;} public void setCategoryLocked(boolean v){categoryLocked=v;} public BigDecimal getUnitsPerPackage(){return unitsPerPackage;} public void setUnitsPerPackage(BigDecimal v){unitsPerPackage=v;} public String getNote(){return note;} public void setNote(String v){note=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
