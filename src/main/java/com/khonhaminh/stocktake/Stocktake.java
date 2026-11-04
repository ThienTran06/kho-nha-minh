package com.khonhaminh.stocktake;
import com.khonhaminh.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="stocktakes")
public class Stocktake {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id") private Product product;
 @Column(name="product_name_snapshot",nullable=false,length=180) private String productNameSnapshot;
 @Column(name="unit_snapshot",nullable=false,length=60) private String unitSnapshot;
 @Column(name="system_quantity",nullable=false,precision=19,scale=3) private BigDecimal systemQuantity;
 @Column(name="actual_quantity",nullable=false,precision=19,scale=3) private BigDecimal actualQuantity;
 @Column(name="difference_quantity",nullable=false,precision=19,scale=3) private BigDecimal differenceQuantity;
 @Column(length=500) private String note;
 @Column(name="counted_at",nullable=false) private LocalDateTime countedAt;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void pre(){if(createdAt==null)createdAt=LocalDateTime.now();if(countedAt==null)countedAt=LocalDateTime.now();}
 public Long getId(){return id;} public Product getProduct(){return product;} public void setProduct(Product v){product=v;}
 public String getProductNameSnapshot(){return productNameSnapshot;} public void setProductNameSnapshot(String v){productNameSnapshot=v;}
 public String getUnitSnapshot(){return unitSnapshot;} public void setUnitSnapshot(String v){unitSnapshot=v;}
 public BigDecimal getSystemQuantity(){return systemQuantity;} public void setSystemQuantity(BigDecimal v){systemQuantity=v;}
 public BigDecimal getActualQuantity(){return actualQuantity;} public void setActualQuantity(BigDecimal v){actualQuantity=v;}
 public BigDecimal getDifferenceQuantity(){return differenceQuantity;} public void setDifferenceQuantity(BigDecimal v){differenceQuantity=v;}
 public String getNote(){return note;} public void setNote(String v){note=v;} public LocalDateTime getCountedAt(){return countedAt;} public void setCountedAt(LocalDateTime v){countedAt=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
